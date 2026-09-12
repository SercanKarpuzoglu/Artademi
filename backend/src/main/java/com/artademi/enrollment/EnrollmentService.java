package com.artademi.enrollment;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.dto.CreateEnrollmentRequest;
import com.artademi.enrollment.dto.EnrollmentMapper;
import com.artademi.enrollment.dto.EnrollmentResponse;
import com.artademi.enrollment.dto.TransferEnrollmentRequest;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.dto.AccrualMapper;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.group.GrupTipi;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.student.StudentStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artademi.kredi.KrediService;

/**
 * Kayit (enrollment) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogrenciId ve grupId ilgili repository'nin
 * {@code findScopedById} metodu ile cozulur; bulunamazsa (baska tenant'a ait VEYA yok)
 * {@link NotFoundException} (-> 404). Boylece baska tenant'in ogrenci/grup id'siyle kayit
 * olusturulamaz (sizinti yok).
 *
 * <p>Is kurallari (create):
 * <ol>
 *   <li>Ogrenci statusu AKTIF veya DENEME degilse (PASIF/DONDURULMUS) -> 400 ValidationException.</li>
 *   <li>Ayni ogrenci+grup icin AKTIF kayit varsa -> 409 ConflictException (AYRILDI varsa yeni acilir;
 *       DB tarafinda partial unique index ile de zorlanir).</li>
 * </ol>
 *
 * <p>Silme YOK: ayrilma {@link #leave} ile durum=AYRILDI + ayrilmaTarihi=bugun olarak yapilir.
 */
@Service
public class EnrollmentService {

    /** Gruba yazilmaya uygun ogrenci statuleri (PASIF/DONDURULMUS uygun degil). */
    private static final Set<StudentStatus> YAZILABILIR_STATULER =
            Set.of(StudentStatus.AKTIF, StudentStatus.DENEME);

    private final EnrollmentRepository repository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final AccrualRepository accrualRepository;

    private final KrediService krediService;

    public EnrollmentService(EnrollmentRepository repository, StudentRepository studentRepository,
            GroupRepository groupRepository, AccrualRepository accrualRepository,
            KrediService krediService) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.accrualRepository = accrualRepository;
        this.krediService = krediService;
    }

    /** Yeni kayit olusturur; durum AKTIF ile baslar. */
    @Transactional
    public EnrollmentResponse create(CreateEnrollmentRequest req) {
        Student ogrenci = resolveStudent(req.ogrenciId());
        Group grup = resolveGroup(req.grupId());

        if (!YAZILABILIR_STATULER.contains(ogrenci.getStatus())) {
            throw new ValidationException("Bu statüdeki öğrenci gruba yazılamaz");
        }
        if (repository.existsAktifByOgrenciAndGrup(ogrenci.getId(), grup.getId())) {
            throw new ConflictException("Öğrenci bu gruba zaten aktif olarak kayıtlı");
        }
        // Kara liste: engel degil, UYARI. Onaysiz istek 409 KARA_LISTE; onaylanirsa kayit acilir.
        if (ogrenci.isKaraListe() && !Boolean.TRUE.equals(req.karaListeOnayi())) {
            throw new ConflictException(
                    "Kara listede: " + (ogrenci.getKaraListeAciklama() == null ? "sebep girilmemiş"
                            : ogrenci.getKaraListeAciklama()), "KARA_LISTE");
        }

        Enrollment yeni = EnrollmentMapper.toNewEntity(ogrenci, grup, req.kayitTarihi());
        // Plan (GRUP tipinde; OZEL derste plan yok = kayit). DONEMLIK -> grubun donemi kayda yazilir.
        OdemePlani plan = grup.getTip() == GrupTipi.GRUP
                ? (req.odemePlani() == null ? OdemePlani.AYLIK : req.odemePlani())
                : null;
        planiUygula(yeni, grup, plan);
        Enrollment saved = repository.save(yeni);
        // URUN KARARI (2026-09-12): plan secimi statuyu belirler. AYLIK/DONEMLIK (ve OZEL ders) = taahhut ->
        // ogrenci AKTIF olur; DENEME (deneme dersi) -> DENEME kalir, para/kredi yok. Onceki karar (10 Eylul,
        // "elle kalsin") Dalga E'nin plan modaliyla anlamsizlasmisti: plan secen ogrencinin Deneme kalmasi ve
        // Donemlik'te tahakkuk kesilip Aylik'ta kesilmemesi tutarsizdi.
        if (plan != OdemePlani.DENEME) {
            aktiflestir(ogrenci);
            krediService.kayitSonrasiKredi(saved);
        }
        return EnrollmentResponse.from(saved);
    }

    /**
     * Deneme dersi kaydini plana gecirir (AYLIK/DONEMLIK): kredi + tahakkuk o anda acilir, ogrenci AKTIF olur.
     * Yalniz plan DENEME olan AKTIF kayitta; digerinde 400.
     */
    @Transactional
    public EnrollmentResponse planaGecir(Long id, OdemePlani yeniPlan) {
        Enrollment e = findOrThrow(id);
        if (e.getDurum() != EnrollmentDurumu.AKTIF) {
            throw new ValidationException("Ayrılmış kayıt plana geçirilemez");
        }
        if (e.getOdemePlani() != OdemePlani.DENEME) {
            throw new ValidationException("Bu kayıt zaten bir planda (" + e.getOdemePlani() + ")");
        }
        if (yeniPlan == null || yeniPlan == OdemePlani.DENEME) {
            throw new ValidationException("Aylık ya da Dönemlik seçin");
        }
        planiUygula(e, e.getGrup(), yeniPlan);
        // Kredi, plana gecis gununden itibaren hesaplanir (deneme gunleri faturalanmaz).
        e.setKayitTarihi(LocalDate.now());
        aktiflestir(e.getOgrenci());
        krediService.kayitSonrasiKredi(e);
        return EnrollmentResponse.from(e);
    }

    private static void planiUygula(Enrollment e, Group grup, OdemePlani plan) {
        e.setOdemePlani(plan);
        if (plan == OdemePlani.DONEMLIK) {
            if (grup.getDonem() == null) {
                throw new ValidationException("Grubun dönemi tanımlı değil; dönemlik kayıt yapılamaz");
            }
            e.setDonem(grup.getDonem());
        } else {
            e.setDonem(null);
        }
    }

    /** DENEME -> AKTIF (PASIF/DONDURULMUS zaten gruba yazilamaz; AKTIF'e dokunulmaz). */
    private static void aktiflestir(Student ogrenci) {
        if (ogrenci.getStatus() == StudentStatus.DENEME) {
            ogrenci.setStatus(StudentStatus.AKTIF);
        }
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(Long id) {
        return EnrollmentResponse.from(findOrThrow(id));
    }

    /** Ayrilma: kayit silinmez; durum AYRILDI, ayrilmaTarihi bugun. */
    @Transactional
    public EnrollmentResponse leave(Long id) {
        Enrollment e = findOrThrow(id);
        e.setDurum(EnrollmentDurumu.AYRILDI);
        e.setAyrilmaTarihi(LocalDate.now());
        return EnrollmentResponse.from(e);
    }

    /**
     * Ogrenciyi bir GRUP dersinden baska bir GRUP dersine transfer eder (tek transaction): eski kayit
     * AYRILDI, yeni gruba AKTIF kayit acilir, ve o donem icin aidat farki otomatik tahakkuk edilir.
     *
     * <p><b>Kapsam:</b> yalniz GRUP↔GRUP. Eski veya yeni grup OZEL ise -> 400 (ozel derste aylik aidat
     * yoktur; transfer kavrami gecersiz).
     *
     * <p><b>Aidat farki (donem = req.donem veya bugunun ayi):</b> eski grubun o donem aidat tahakkuku
     * ZATEN urediyse: eski grup icin NEGATIF tahakkuk (iade = −eskiAidat) + yeni grup icin POZITIF
     * tahakkuk (+yeniAidat) acilir; net = aidat farki, bakiye dogru hesaplanir. Eski grubun o donem
     * tahakkuku YOKSA hicbir tahakkuk uretilmez (olusmamis borcun iadesi olmaz; bir sonraki otomatik
     * tahakkuk yeni grubu zaten keser).
     */
    @Transactional
    public EnrollmentResponse transfer(Long id, TransferEnrollmentRequest req) {
        Enrollment mevcut = findOrThrow(id);
        Student ogrenci = mevcut.getOgrenci();
        Group eskiGrup = mevcut.getGrup();
        Group yeniGrup = resolveGroup(req.yeniGrupId());

        if (eskiGrup.getTip() != GrupTipi.GRUP || yeniGrup.getTip() != GrupTipi.GRUP) {
            throw new ValidationException("Transfer yalnızca grup dersleri arasında yapılır");
        }
        if (!YAZILABILIR_STATULER.contains(ogrenci.getStatus())) {
            throw new ValidationException("Bu statüdeki öğrenci gruba yazılamaz");
        }
        // Ayni gruba veya zaten aktif oldugu gruba transfer -> 409 (mevcut hala AKTIF iken kontrol).
        if (repository.existsAktifByOgrenciAndGrup(ogrenci.getId(), yeniGrup.getId())) {
            throw new ConflictException("Öğrenci bu gruba zaten aktif olarak kayıtlı");
        }

        // 1) Eski kaydi AYRILDI yap (leave mantigi).
        mevcut.setDurum(EnrollmentDurumu.AYRILDI);
        mevcut.setAyrilmaTarihi(LocalDate.now());

        // 2) Yeni gruba AKTIF kayit — plan tasinir (DONEMLIK'te yeni grubun donemi; yoksa AYLIK'a duser).
        Enrollment yeniKayit = EnrollmentMapper.toNewEntity(ogrenci, yeniGrup, LocalDate.now());
        OdemePlani tasinanPlan = mevcut.getOdemePlani() == OdemePlani.DONEMLIK && yeniGrup.getDonem() == null
                ? OdemePlani.AYLIK
                : (mevcut.getOdemePlani() == null ? OdemePlani.AYLIK : mevcut.getOdemePlani());
        planiUygula(yeniKayit, yeniGrup, tasinanPlan);
        Enrollment yeni = repository.save(yeniKayit);

        // 3) Aidat farki — yalnizca eski grubun o donem tahakkuku zaten urediyse.
        String donem = (req.donem() != null && !req.donem().isBlank())
                ? req.donem()
                : YearMonth.now().toString();
        if (accrualRepository.existsByOgrenciAndGrupAndDonem(ogrenci.getId(), eskiGrup.getId(), donem)) {
            accrualRepository.save(AccrualMapper.toNewEntity(ogrenci, eskiGrup, donem,
                    eskiGrup.getAylikAidat().negate(),
                    eskiGrup.getAd() + " grubundan ayrılma iadesi (" + donem + ")"));
            accrualRepository.save(AccrualMapper.toNewEntity(ogrenci, yeniGrup, donem,
                    yeniGrup.getAylikAidat(),
                    yeniGrup.getAd() + " grubuna geçiş (" + donem + ")"));
        }

        return EnrollmentResponse.from(yeni);
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> search(Long ogrenciId, Long grupId, EnrollmentDurumu durum,
            Pageable pageable) {
        Specification<Enrollment> spec = Specification
                .where(EnrollmentSpecifications.hasOgrenci(ogrenciId))
                .and(EnrollmentSpecifications.hasGrup(grupId))
                .and(EnrollmentSpecifications.hasDurum(durum));
        return repository.findAll(spec, pageable)
                .map(EnrollmentResponse::from);
    }

    /**
     * ogrenciId'yi tenant-guvenli ({@code findScopedById}) cozer. Bulunamazsa (baska tenant'a ait
     * veya yok) -> 404, sizinti yok.
     */
    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Enrollment findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Kayıt bulunamadı: " + id));
    }
}
