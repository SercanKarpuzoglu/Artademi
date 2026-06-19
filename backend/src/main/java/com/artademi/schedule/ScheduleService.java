package com.artademi.schedule;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.schedule.dto.CreateScheduleRequest;
import com.artademi.schedule.dto.ScheduleMapper;
import com.artademi.schedule.dto.ScheduleResponse;
import com.artademi.schedule.dto.UpdateScheduleRequest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Program (haftalik ders saati) is kurallari. {@code @Transactional} oldugundan cagrildiginda global
 * tenant filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set
 * edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen {@code grupId}, {@code groupRepository
 * .findScopedById} ile cozulur; bulunamazsa (baska tenant'a ait VEYA yok) {@link NotFoundException}
 * (-> 404). Boylece baska tenant'in grup id'siyle program olusturulamaz/guncellenemez (sizinti yok).
 *
 * <p>Cakisma kurallari (ayni tenant — filtre otomatik): ayni gun + saat ortusmesi olan AKTIF
 * programlar arasinda
 * <ul>
 *   <li><b>Salon cakismasi</b>: bu grubun salonu varsa ve ortusen aktif bir programin grubunun
 *       salonu AYNI ise -> 409 CONFLICT. (Grup OZEL/salonsuzsa salon kontrolu ATLANIR.)</li>
 *   <li><b>Ogretmen cakismasi</b>: ortusen aktif bir programin grubunun ogretmeni AYNI ise ->
 *       409 CONFLICT (bir ogretmen ayni anda iki yerde olamaz).</li>
 * </ul>
 * Update'te kayit kendi id'sini haric tutar.
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur.
 */
@Service
public class ScheduleService {

    private static final DateTimeFormatter SAAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleRepository repository;
    private final GroupRepository groupRepository;

    public ScheduleService(ScheduleRepository repository, GroupRepository groupRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
    }

    /** Yeni program olusturur; aktif true ile baslar. Cakisma kontrolu uygulanir. */
    @Transactional
    public ScheduleResponse create(CreateScheduleRequest req) {
        Group grup = resolveGroup(req.grupId());
        cakismaKontrol(grup, req.gun(), req.baslangicSaati(), req.bitisSaati(), null);
        Schedule saved = repository.save(ScheduleMapper.toNewEntity(req, grup));
        return ScheduleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(Long id) {
        return ScheduleResponse.from(findOrThrow(id));
    }

    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest req) {
        Schedule schedule = findOrThrow(id);
        Group grup = resolveGroup(req.grupId());
        cakismaKontrol(grup, req.gun(), req.baslangicSaati(), req.bitisSaati(), schedule.getId());
        ScheduleMapper.applyUpdate(schedule, req, grup);
        return ScheduleResponse.from(schedule);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public ScheduleResponse changeActive(Long id, boolean aktif) {
        Schedule schedule = findOrThrow(id);
        schedule.setAktif(aktif);
        return ScheduleResponse.from(schedule);
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<ScheduleResponse> search(Long grupId, HaftaGunu gun, Boolean aktif, Pageable pageable) {
        Specification<Schedule> spec = Specification
                .where(ScheduleSpecifications.hasGrup(grupId))
                .and(ScheduleSpecifications.hasGun(gun))
                .and(ScheduleSpecifications.hasAktif(aktif));
        return repository.findAll(spec, pageable)
                .map(ScheduleResponse::from);
    }

    /**
     * Bir grubun haftalik programi (gun, ardindan baslangic saati sirali). grupId tenant-guvenli
     * ({@code findScopedById}) cozulur; baska tenant'a ait/yok ise -> 404.
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> listByGroup(Long grupId) {
        resolveGroup(grupId);
        // NOT: gun @Enumerated(STRING) oldugundan SQL "ORDER BY gun" alfabetik siralar (CARSAMBA
        // once gelir). Hafta sirasi (Pazartesi->Pazar) icin enum ordinal'ine gore JAVA'da siralanir.
        return repository.findByGrupId(grupId).stream()
                .sorted(Comparator.comparing(Schedule::getGun)
                        .thenComparing(Schedule::getBaslangicSaati))
                .map(ScheduleResponse::from)
                .toList();
    }

    /**
     * grupId'yi tenant-guvenli ({@code findScopedById}) cozer. Bulunamazsa (baska tenant'a ait veya
     * yok) -> 404, sizinti yok.
     */
    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Schedule findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Program bulunamadı: " + id));
    }

    /**
     * Ayni gun + saat ortusmesi olan aktif programlara karsi salon ve ogretmen cakismasini kontrol
     * eder. {@code haricId} doluysa (update) o kayit haric tutulur. Cakisma varsa 409 CONFLICT.
     * Sorgu JPQL/tenant-filtreli oldugu icin yalnizca ayni tenant'in kayitlari degerlendirilir.
     */
    private void cakismaKontrol(Group grup, HaftaGunu gun, LocalTime baslangic, LocalTime bitis,
            Long haricId) {
        Long salonId = grup.getSalon() == null ? null : grup.getSalon().getId();
        Long ogretmenId = grup.getOgretmen() == null ? null : grup.getOgretmen().getId();
        String aralik = gunSaat(gun, baslangic, bitis);

        for (Schedule mevcut : repository.findOverlappingActive(gun, baslangic, bitis)) {
            if (haricId != null && haricId.equals(mevcut.getId())) {
                continue;
            }
            Group mevcutGrup = mevcut.getGrup();
            // Salon cakismasi: bu grubun salonu varsa ve ortusen kaydin grubunun salonu AYNI ise.
            if (salonId != null && mevcutGrup.getSalon() != null
                    && salonId.equals(mevcutGrup.getSalon().getId())) {
                throw new ConflictException("Salon çakışması: " + grup.getSalon().getAd()
                        + " salonu " + aralik + " aralığında " + mevcutGrup.getAd()
                        + " grubu tarafından kullanılıyor");
            }
            // Ogretmen cakismasi: ortusen kaydin grubunun ogretmeni AYNI ise.
            if (ogretmenId != null && mevcutGrup.getOgretmen() != null
                    && ogretmenId.equals(mevcutGrup.getOgretmen().getId())) {
                throw new ConflictException("Öğretmen çakışması: " + grup.getOgretmen().getAd()
                        + " " + grup.getOgretmen().getSoyad() + " öğretmeni " + aralik
                        + " aralığında " + mevcutGrup.getAd() + " grubunda dolu");
            }
        }
    }

    private static String gunSaat(HaftaGunu gun, LocalTime baslangic, LocalTime bitis) {
        return gun + " " + baslangic.format(SAAT) + "-" + bitis.format(SAAT);
    }
}
