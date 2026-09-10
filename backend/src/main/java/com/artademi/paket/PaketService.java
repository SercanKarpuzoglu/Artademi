package com.artademi.paket;

import com.artademi.attendance.YoklamaDurumu;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.Accrual;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.dto.AccrualMapper;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.paket.dto.PaketResponse;
import com.artademi.paket.dto.PaketSatRequest;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ders paketi (kontor) satisi ve tuketimi.
 *
 * <h2>Kontor dusumu</h2>
 * Yoklama kaydedilirken {@link #yoklamaDegisti} cagrilir:
 * <ul>
 *   <li>{@code GELDI}   → kontor duser</li>
 *   <li>{@code GELMEDI} → kontor DUSER (habersiz gelmeme; okul dersi tahsis etti)</li>
 *   <li>{@code IZINLI}  → dusmez, varsa dusum GERI ALINIR (onceden haber verilmis)</li>
 * </ul>
 * Bu ayrim mevcut {@link YoklamaDurumu} ile birebir ortusur; "haber verdi mi" diye ayri
 * bir alan eklemeye gerek kalmadi.
 *
 * <h2>⚠️ Kalan ders saklanmaz</h2>
 * Tuketilen her ders bir {@link PaketKullanim} SATIRIDIR; kalan = toplam - satir sayisi.
 * Sayac tutulsaydi yoklama duzeltmelerinde (GELDI → IZINLI) sapar ve sapma sessiz kalirdi.
 *
 * <h2>⚠️ Dusum akisi yoklamayi ASLA engellemez</h2>
 * Kontoru biten ogrenci derse yazilmaya devam eder; sadece dusum yapilmaz. Yoklama alinamamasi,
 * ogretmeni sistem disina iter — paket takibi bunu hak etmez.
 */
@Service
public class PaketService {

    private static final Logger log = LoggerFactory.getLogger(PaketService.class);

    private final DersPaketiRepository repository;
    private final PaketKullanimRepository kullanimlar;
    private final StudentRepository students;
    private final GroupRepository groups;
    private final AccrualRepository accruals;

    public PaketService(DersPaketiRepository repository, PaketKullanimRepository kullanimlar,
            StudentRepository students, GroupRepository groups, AccrualRepository accruals) {
        this.repository = repository;
        this.kullanimlar = kullanimlar;
        this.students = students;
        this.groups = groups;
        this.accruals = accruals;
    }

    // ---------- satis / listeleme ----------

    @Transactional(readOnly = true)
    public List<PaketResponse> list(Long ogrenciId) {
        List<DersPaketi> liste = ogrenciId != null
                ? repository.ogrenciyeGore(ogrenciId)
                : repository.tumu();
        return yanitla(liste);
    }

    @Transactional(readOnly = true)
    public PaketResponse get(Long id) {
        DersPaketi p = bul(id);
        return PaketResponse.from(p, kullanimlar.countByPaketId(id), LocalDate.now());
    }

    /**
     * Paket satar ve PESIN tek tahakkuk uretir.
     *
     * <p>Tahakkuk paketin tutariyla, paket adi aciklamasiyla olusur; boylece finans
     * ekranindan bakan kisi bunun bir paket satisi oldugunu gorur.
     */
    @Transactional
    public PaketResponse sat(PaketSatRequest req) {
        Student ogrenci = students.findScopedById(req.ogrenciId())
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + req.ogrenciId()));
        Group grup = req.grupId() == null ? null : groups.findScopedById(req.grupId())
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + req.grupId()));

        DersPaketi paket = repository.save(DersPaketi.of(ogrenci, req.ad().trim(), grup,
                req.toplamDers(), req.tutar(), req.satisTarihiOrBugun(),
                req.sonKullanmaTarihi(), req.aciklama()));

        Accrual tahakkuk = accruals.save(AccrualMapper.toNewEntity(ogrenci, grup, null,
                req.tutar(), "Ders paketi: " + paket.getAd()));
        paket.accrualBagla(tahakkuk);

        return PaketResponse.from(paket, 0, LocalDate.now());
    }

    /**
     * Paketi iptal eder; kontor dusumu artik bu paketten yapilmaz.
     *
     * <p>Tahakkuk OTOMATIK SILINMEZ: tahsilat yapilmis olabilir ve iade/mahsup kurumun
     * karari olan bir finans islemidir. Silmek, yapilmis tahsilati sahipsiz birakirdi.
     */
    @Transactional
    public PaketResponse iptal(Long id) {
        DersPaketi p = bul(id);
        if (p.getDurum() == PaketDurumu.IPTAL) {
            throw new ConflictException("Bu paket zaten iptal edilmiş.");
        }
        p.iptalEt();
        return PaketResponse.from(p, kullanimlar.countByPaketId(id), LocalDate.now());
    }

    // ---------- kontor dusumu (yoklamadan cagrilir) ----------

    /**
     * Yoklama durumu degistiginde kontor dusumunu gunceller.
     *
     * <p>Idempotent: ayni durumla tekrar cagrilmasi bir sey degistirmez. Cagiran taraf
     * (yoklama servisi) her kayitta bunu cagirabilir.
     *
     * @param grupId oturumun grubu; pakete bagli grup varsa oncelik onda
     */
    @Transactional
    public void yoklamaDegisti(Long ogrenciId, Long oturumId, Long grupId, YoklamaDurumu durum,
            LocalDate tarih) {
        Optional<PaketKullanim> mevcut = kullanimlar.findByOgrenciIdAndOturumId(ogrenciId, oturumId);

        if (durum == YoklamaDurumu.IZINLI) {
            // Onceden haber verilmis: kontor yanmaz. Daha once dusulduyse GERI ALINIR.
            mevcut.ifPresent(kullanimlar::delete);
            return;
        }

        // GELDI veya GELMEDI: kontor duser. Zaten dusulduyse tekrar dusulmez.
        if (mevcut.isPresent()) {
            return;
        }

        secilecekPaket(ogrenciId, grupId, tarih).ifPresent(paket ->
                kullanimlar.save(PaketKullanim.of(paket.getId(), oturumId, ogrenciId, tarih)));
        // Uygun paket yoksa hicbir sey yapilmaz: ogrenci aylik aidatli olabilir ya da
        // kontoru bitmis olabilir. Yoklama HER DURUMDA kaydedilmeye devam eder.
    }

    /**
     * Bu ders icin kredi var mi (Dalga E uyarisi): bu oturumda zaten dusulmus ya da dusulebilecek paket var.
     * Yoklamayi ENGELLEMEZ; yalnizca ofise bildirim icin bakilir.
     */
    @Transactional(readOnly = true)
    public boolean dersIcinKrediVar(Long ogrenciId, Long oturumId, Long grupId, LocalDate tarih) {
        return kullanimlar.findByOgrenciIdAndOturumId(ogrenciId, oturumId).isPresent()
                || secilecekPaket(ogrenciId, grupId, tarih).isPresent();
    }

    /**
     * Dusum yapilacak paketi secer.
     *
     * <p>Sira: (1) oturumun grubuna BAGLI paketler, (2) grubu olmayan genel paketler.
     * Her iki kumede de FIFO (en eski satis once) — aksi halde suresi yaklasan paket bosta
     * kalirken yeni paket harcanir ve ogrenci hak kaybeder.
     *
     * <p>Kontoru dolmus ve suresi gecmis paketler elenir.
     */
    private Optional<DersPaketi> secilecekPaket(Long ogrenciId, Long grupId, LocalDate tarih) {
        List<DersPaketi> aktifler = repository.aktifPaketler(ogrenciId).stream()
                .filter(p -> p.kullanilabilirMi(tarih))
                .filter(p -> kullanimlar.countByPaketId(p.getId()) < p.getToplamDers())
                .toList();

        Optional<DersPaketi> gruba = aktifler.stream()
                .filter(p -> p.getGrup() != null && p.getGrup().getId().equals(grupId))
                .findFirst();
        if (gruba.isPresent()) {
            return gruba;
        }
        return aktifler.stream().filter(p -> p.getGrup() == null).findFirst();
    }

    // ---------- yardimcilar ----------

    private List<PaketResponse> yanitla(List<DersPaketi> liste) {
        LocalDate bugun = LocalDate.now();
        if (liste.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> sayimlar = new HashMap<>();
        kullanimlar.paketBasinaKullanim(liste.stream().map(DersPaketi::getId).toList())
                .forEach(r -> sayimlar.put((Long) r[0], (Long) r[1]));
        return liste.stream()
                .map(p -> PaketResponse.from(p, sayimlar.getOrDefault(p.getId(), 0L), bugun))
                .toList();
    }

    /** ⚠️ findById DEGIL: PK-find tenant filtresine tabi degildir. */
    private DersPaketi bul(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Ders paketi bulunamadı: " + id));
    }
}
