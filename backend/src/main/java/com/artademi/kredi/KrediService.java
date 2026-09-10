package com.artademi.kredi;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.donem.Donem;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.enrollment.OdemePlani;
import com.artademi.finance.Accrual;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.dto.AccrualMapper;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.group.GrupTipi;
import com.artademi.indirim.IndirimService;
import com.artademi.indirim.IndirimSonucu;
import com.artademi.paket.DersPaketi;
import com.artademi.paket.DersPaketiRepository;
import com.artademi.paket.PaketKaynagi;
import com.artademi.schedule.Schedule;
import com.artademi.schedule.ScheduleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Donem & kredi (Dalga E). KREDI = mevcut ders paketi altyapisi; bu servis paketleri OTOMATIK uretir:
 * <ul>
 *   <li>DONEMLIK kayit → kayit aninda: donemdeki (kayit tarihinden itibaren) ders sayisi kadar paket + TEK
 *       tahakkuk (donemlik ucret, ogrenci indirimi uygulanir).</li>
 *   <li>AYLIK kayit → kayit aninda icinde bulunulan ayin kredisi; sonraki aylar Otomatik Tahakkuk'ta
 *       ({@link #aylikKredileriUret}). Aylik kredi paketi 0 TL'dir; aidat ayri tahakkuktur.</li>
 * </ul>
 * Ders sayisi = grubun AKTIF ders saatlerinin [from,to] icindeki gun sayisi (duz takvim; tatil dusumu yok —
 * urun karari 2026-09-10). Kontor dusumu yoklamadan (PaketService.yoklamaDegisti) — degismedi.
 */
@Service
public class KrediService {

    private static final Logger log = LoggerFactory.getLogger(KrediService.class);

    private final GroupRepository groups;
    private final ScheduleRepository schedules;
    private final DersPaketiRepository paketler;
    private final AccrualRepository accruals;
    private final EnrollmentRepository enrollments;
    private final IndirimService indirimler;

    public KrediService(GroupRepository groups, ScheduleRepository schedules, DersPaketiRepository paketler,
            AccrualRepository accruals, EnrollmentRepository enrollments, IndirimService indirimler) {
        this.groups = groups;
        this.schedules = schedules;
        this.paketler = paketler;
        this.accruals = accruals;
        this.enrollments = enrollments;
        this.indirimler = indirimler;
    }

    /** [from, to] araliginda grubun aktif programina gore ders sayisi (duz takvim). */
    @Transactional(readOnly = true)
    public int dersSayisi(Long grupId, LocalDate from, LocalDate to) {
        List<Schedule> program = schedules.findByGrupId(grupId).stream().filter(Schedule::isAktif).toList();
        if (program.isEmpty() || to == null || from == null || to.isBefore(from)) {
            return 0;
        }
        int[] gunSayaci = new int[7];
        for (Schedule s : program) {
            gunSayaci[s.getGun().ordinal()]++;
        }
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            n += gunSayaci[d.getDayOfWeek().getValue() - 1];
        }
        return n;
    }

    /** Plan secim ekrani icin hesap; uygun degilse neden doner (400 degil). */
    @Transactional(readOnly = true)
    public KayitOnizleme onizle(Long grupId, OdemePlani plan, LocalDate tarih) {
        Group g = groups.findScopedById(grupId).orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
        LocalDate t = tarih == null ? LocalDate.now() : tarih;
        if (g.getTip() != GrupTipi.GRUP) {
            return KayitOnizleme.uygunDegil(plan, "Özel derste plan seçimi yok (ders başı ücret)");
        }
        int haftalik = (int) schedules.findByGrupId(grupId).stream().filter(Schedule::isAktif).count();
        if (plan == OdemePlani.DONEMLIK) {
            Donem d = g.getDonem();
            if (d == null) {
                return KayitOnizleme.uygunDegil(plan, "Grubun dönemi tanımlı değil (Ders Ücretleri / Gruplar → Düzenle)");
            }
            if (g.getDonemlikUcret() == null) {
                return KayitOnizleme.uygunDegil(plan, "Grubun dönemlik ücreti girilmemiş");
            }
            LocalDate from = t.isBefore(d.getBaslangic()) ? d.getBaslangic() : t;
            if (from.isAfter(d.getBitis())) {
                return KayitOnizleme.uygunDegil(plan, "Dönem bitmiş (" + d.getAd() + " · " + d.getBitis() + ")");
            }
            return new KayitOnizleme(plan, true, null, d.getId(), d.getAd(), from, d.getBitis(), haftalik,
                    dersSayisi(grupId, from, d.getBitis()), g.getDonemlikUcret());
        }
        if (g.getAylikAidat() == null) {
            return KayitOnizleme.uygunDegil(plan, "Grubun aylık ücreti girilmemiş");
        }
        LocalDate aySonu = YearMonth.from(t).atEndOfMonth();
        return new KayitOnizleme(plan, true, null, null, null, t, aySonu, haftalik,
                dersSayisi(grupId, t, aySonu), g.getAylikAidat());
    }

    /**
     * Kayit sonrasi kredi (EnrollmentService.create'ten). DONEMLIK: paket + tek tahakkuk. AYLIK: bu ayin kredisi
     * (tahakkuk Otomatik Tahakkuk'tan). Program yoksa (ders saati tanimlanmamis) paket acilmaz, log yazilir.
     */
    @Transactional
    public void kayitSonrasiKredi(Enrollment e) {
        Group g = e.getGrup();
        if (g == null || g.getTip() != GrupTipi.GRUP) {
            return;
        }
        OdemePlani plan = e.getOdemePlani() == null ? OdemePlani.AYLIK : e.getOdemePlani();
        LocalDate kayit = e.getKayitTarihi() == null ? LocalDate.now() : e.getKayitTarihi();
        KayitOnizleme on = onizle(g.getId(), plan, kayit);
        if (!on.uygun()) {
            throw new ValidationException(on.neden());
        }
        if (on.dersSayisi() == 0) {
            log.warn("Kredi açılmadı: grubun ders saati yok (grup={}, öğrenci={})", g.getId(), e.getOgrenci().getId());
            return;
        }
        if (plan == OdemePlani.DONEMLIK) {
            IndirimSonucu indirim = indirimler.hesapla(e.getOgrenci().getId(), g.getId(), on.baslangic(), on.ucret());
            String donemAd = on.donemAd();
            DersPaketi paket = paketler.save(DersPaketi.of(e.getOgrenci(),
                    "Dönemlik kredi · " + donemAd + " · " + g.getAd(), g, on.dersSayisi(), indirim.net(), kayit,
                    on.bitis(), "Kayıtta otomatik: " + on.baslangic() + "–" + on.bitis() + ", haftada "
                            + on.haftalikDers() + " ders"));
            paket.setKaynak(PaketKaynagi.KAYIT_DONEMLIK);
            paket.setKaynakDonem(YearMonth.from(kayit).toString());
            Accrual tahakkuk = AccrualMapper.toNewEntity(e.getOgrenci(), g, YearMonth.from(kayit).toString(),
                    indirim.net(), "Dönemlik ücret · " + donemAd + " · " + g.getAd()
                            + (indirim.var() ? " (indirim: " + indirim.aciklama() + ")" : ""));
            if (indirim.var()) {
                tahakkuk.setBrutTutar(indirim.brut());
                tahakkuk.setIndirimTutar(indirim.indirim());
                tahakkuk.setIndirimAciklama(indirim.aciklama());
            }
            paket.accrualBagla(accruals.save(tahakkuk));
            return;
        }
        aylikKredi(e, YearMonth.from(kayit), kayit);
    }

    /**
     * Otomatik Tahakkuk'tan (persist modunda): donem icin AYLIK plandaki aktif kayitlara o ayin kredisi.
     * Mukerrer kalkani: (ogrenci, grup, kaynak_donem) varsa atlanir.
     *
     * @return acilan kredi paketi sayisi
     */
    @Transactional
    public int aylikKredileriUret(String donem) {
        YearMonth ay = YearMonth.parse(donem);
        int n = 0;
        for (Enrollment e : enrollments.findAktifAidatliKayitlar()) {
            if (e.getOdemePlani() == OdemePlani.DONEMLIK) {
                continue;
            }
            LocalDate from = ay.atDay(1);
            if (e.getKayitTarihi() != null && e.getKayitTarihi().isAfter(from)) {
                from = e.getKayitTarihi();
            }
            if (aylikKredi(e, ay, from)) {
                n++;
            }
        }
        return n;
    }

    private boolean aylikKredi(Enrollment e, YearMonth ay, LocalDate from) {
        Group g = e.getGrup();
        String kaynakDonem = ay.toString();
        if (paketler.existsAylikKredi(e.getOgrenci().getId(), g.getId(), kaynakDonem)) {
            return false;
        }
        LocalDate to = ay.atEndOfMonth();
        int ders = dersSayisi(g.getId(), from, to);
        if (ders == 0) {
            return false;
        }
        DersPaketi paket = paketler.save(DersPaketi.of(e.getOgrenci(), "Aylık kredi · " + kaynakDonem + " · " + g.getAd(),
                g, ders, BigDecimal.ZERO.setScale(2), from, to,
                "Aylık kayıt: " + from + "–" + to + " (aidat ayrı tahakkuk)"));
        paket.setKaynak(PaketKaynagi.AYLIK_KREDI);
        paket.setKaynakDonem(kaynakDonem);
        return true;
    }

    /**
     * Yoklamada GELDI isaretlenen ogrencinin kredisi yoksa ofise uyari gerekli mi? DONEMLIK'te her zaman
     * (donem doldu / kredi bitti). AYLIK'te yalniz o ayin kredileri zaten uretilmisse (aksi halde "Otomatik
     * Tahakkuk henuz calismadi" gurultusu olurdu).
     */
    @Transactional(readOnly = true)
    public boolean krediUyarisiGerekli(Enrollment e, LocalDate tarih) {
        if (e.getOdemePlani() == OdemePlani.DONEMLIK) {
            return true;
        }
        return paketler.existsAylikKrediDonem(YearMonth.from(tarih).toString());
    }
}
