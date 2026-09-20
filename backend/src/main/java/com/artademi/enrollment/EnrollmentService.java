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
import java.math.BigDecimal;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository repository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final AccrualRepository accrualRepository;

    private final KrediService krediService;
    private final com.artademi.paket.PaketService paketService;
    private final com.artademi.indirim.IndirimService indirimler;

    public EnrollmentService(EnrollmentRepository repository, StudentRepository studentRepository,
            GroupRepository groupRepository, AccrualRepository accrualRepository,
            KrediService krediService, com.artademi.paket.PaketService paketService,
            com.artademi.indirim.IndirimService indirimler) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.accrualRepository = accrualRepository;
        this.krediService = krediService;
        this.paketService = paketService;
        this.indirimler = indirimler;
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
     * <p><b>Para, PLANA gore hesaplanir</b> (2026-09-20'de duzeltildi; oncesinde her plan icin
     * {@code aylikAidat} kullaniliyordu):
     * <ul>
     *   <li><b>AYLIK:</b> eski grubun o donem aidat tahakkuku ZATEN urediyse eski grup icin NEGATIF
     *       (−eskiAidat) + yeni grup icin POZITIF (+yeniAidat) tahakkuk; net = aidat farki. Eski
     *       grubun o donem tahakkuku YOKSA hicbir sey uretilmez (olusmamis borcun iadesi olmaz;
     *       sonraki otomatik tahakkuk yeni grubu zaten keser).</li>
     *   <li><b>DONEMLIK:</b> eski grubun KULLANILMAYAN donem payi iade edilir (negatif tahakkuk,
     *       kalan derse gore orantili, ogrenci indirimi uygulanmis); yeni grubun ucreti ve kredisi
     *       {@link KrediService#kayitSonrasiKredi} tarafindan (yine orantili) acilir.</li>
     * </ul>
     *
     * <p>⚠️ <b>Duzeltilen iki hata (2026-09-20):</b>
     * <ol>
     *   <li>{@code aylikAidat} NULLABLE'dir (yalniz donemlik calisan grupta bos olur) ve kod
     *       {@code .negate()} cagiriyordu → yalniz donemlik ucreti olan gruptan transfer 500
     *       veriyordu. Artik eksik ucret 0 sayilir ve o satir hic yazilmaz.</li>
     *   <li>Transfer {@code kayitSonrasiKredi}'yi HIC cagirmiyordu → ogrenci yeni grupta
     *       KONTORSUZ kaliyor, her derste "kredi bitti" uyarisi uretiliyordu.</li>
     * </ol>
     *
     * <p>Eski grubun kalan kontorleri iptal edilir: ogrenci o gruptan ayrildi, kontorler
     * kullanilamaz durumda kalir ve "kalan kredi" kartinda hayalet gorunurdu.
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

        String donem = (req.donem() != null && !req.donem().isBlank())
                ? req.donem()
                : YearMonth.now().toString();

        // 3) Eski grubun parasi geri verilir; kalan kontorleri iptal edilir.
        if (tasinanPlan == OdemePlani.DONEMLIK) {
            donemlikIade(ogrenci, eskiGrup, donem);
        } else {
            aylikAidatFarki(ogrenci, eskiGrup, yeniGrup, donem);
        }
        paketService.krediIptalEt(ogrenci.getId(), eskiGrup.getId());

        // 4) Yeni grubun kredisi (ve DONEMLIK'te ucreti) — eskiden HIC acilmiyordu.
        krediService.kayitSonrasiKredi(yeni);

        return EnrollmentResponse.from(yeni);
    }

    /**
     * AYLIK planda aidat farki: eski gruba iade (negatif), yeni gruba gecis (pozitif).
     *
     * <p>Yalnizca eski grubun o donem tahakkuku zaten urediyse calisir. Ucreti girilmemis grup
     * (aylikAidat NULL) icin o satir HIC yazilmaz — eskiden burada {@code .negate()} NPE veriyordu.
     */
    private void aylikAidatFarki(Student ogrenci, Group eskiGrup, Group yeniGrup, String donem) {
        if (!accrualRepository.existsByOgrenciAndGrupAndDonem(ogrenci.getId(), eskiGrup.getId(), donem)) {
            return;
        }
        BigDecimal eski = eskiGrup.getAylikAidat();
        BigDecimal yenisi = yeniGrup.getAylikAidat();
        if (eski != null && eski.signum() != 0) {
            accrualRepository.save(AccrualMapper.toNewEntity(ogrenci, eskiGrup, donem, eski.negate(),
                    eskiGrup.getAd() + " grubundan ayrılma iadesi (" + donem + ")"));
        }
        if (yenisi != null && yenisi.signum() != 0) {
            accrualRepository.save(AccrualMapper.toNewEntity(ogrenci, yeniGrup, donem, yenisi,
                    yeniGrup.getAd() + " grubuna geçiş (" + donem + ")"));
        }
        if (eski == null || yenisi == null) {
            log.warn("Transfer aidat farki eksik: aylik ucreti girilmemis grup var (eski={}, yeni={})",
                    eskiGrup.getId(), yeniGrup.getId());
        }
    }

    /**
     * DONEMLIK planda eski grubun KULLANILMAYAN payi iade edilir (negatif tahakkuk).
     *
     * <p>Tutar bugunden donem sonuna kalan derse gore orantilanir ve ogrencinin indirimi uygulanir —
     * ucret tahsil edilirken de ayni sekilde hesaplanmisti, iade de ayni olculerle olmali. Yeni
     * grubun ucreti ayrica {@code kayitSonrasiKredi} ile (yine orantili) acilir.
     */
    private void donemlikIade(Student ogrenci, Group eskiGrup, String donem) {
        var d = eskiGrup.getDonem();
        if (d == null || eskiGrup.getDonemlikUcret() == null) {
            return;
        }
        LocalDate bugun = LocalDate.now();
        if (bugun.isAfter(d.getBitis())) {
            return; // donem bitmis: iade edilecek kullanilmamis pay yok
        }
        LocalDate from = bugun.isBefore(d.getBaslangic()) ? d.getBaslangic() : bugun;
        BigDecimal iade = KrediService.orantiliUcret(
                eskiGrup.getDonemlikUcret(),
                krediService.dersSayisi(eskiGrup.getId(), from, d.getBitis()),
                krediService.dersSayisi(eskiGrup.getId(), d.getBaslangic(), d.getBitis()));
        if (iade == null || iade.signum() == 0) {
            return;
        }
        BigDecimal net = indirimler.hesapla(ogrenci.getId(), eskiGrup.getId(), from, iade).net();
        accrualRepository.save(AccrualMapper.toNewEntity(ogrenci, eskiGrup, donem, net.negate(),
                eskiGrup.getAd() + " grubundan ayrılma iadesi · kullanılmayan dönem payı"));
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
