package com.artademi.finance;

import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.dto.AccrualGenerationResult;
import com.artademi.finance.dto.AccrualGenerationResult.DenemeOgrenci;
import com.artademi.finance.dto.AccrualGenerationResult.OzetKalemi;
import com.artademi.group.Group;
import com.artademi.student.Student;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artademi.indirim.IndirimService;
import com.artademi.indirim.IndirimSonucu;
import com.artademi.enrollment.OdemePlani;
import com.artademi.kredi.KrediService;

/**
 * Otomatik aylik tahakkuk uretimi. Mevcut {@link Accrual} kayitlari uretilir; YENI ENTITY YOK.
 * {@code @Transactional} oldugundan global tenant filtresi aktif oturumda calisir; tum sorgular
 * yalnizca aktif tenant kapsaminda doner (fail-closed).
 *
 * <p>Uretim mantigi (tek tenant icinde): UYGUN kayitlar = AKTIF enrollment + AKTIF ogrenci +
 * GRUP tipi grup + aylikAidat dolu (OZEL gruplar ders basi oldugundan aylik aidat URETILMEZ).
 * Her uygun (ogrenci, grup) icin o donemde zaten tahakkuk varsa ATLANIR (idempotent — ayni donem
 * tekrar calistirilinca mukerrer olusmaz), yoksa grubun {@code aylikAidat}'i tutariyla yeni Accrual
 * olusturulur.
 *
 * <p>DENEME ogrenci: aidatli gruba kayitli ama statusu DENEME olan ogrenciye tahakkuk uretilmez
 * (statu gecisi kurumun elle karari — 2026-09-09). Unutulmasin diye bu ogrenciler sonucta
 * {@code atlananDenemeOgrenciler} olarak ayri listelenir; sayaclara karismaz.
 *
 * <p>PARA KURALI: toplam ve kalem tutarlari {@link BigDecimal}, scale 2, {@link RoundingMode#HALF_UP}.
 */
@Service
public class AccrualGenerationService {

    private final EnrollmentRepository enrollmentRepository;
    private final AccrualRepository accrualRepository;
    private final IndirimService indirimService;
    private final KrediService krediService;

    public AccrualGenerationService(EnrollmentRepository enrollmentRepository,
            AccrualRepository accrualRepository, IndirimService indirimService, KrediService krediService) {
        this.enrollmentRepository = enrollmentRepository;
        this.accrualRepository = accrualRepository;
        this.indirimService = indirimService;
        this.krediService = krediService;
    }

    /** Donem icin tahakkuklari URETIR ve kaydeder. Ayni donem tekrar calisirsa mukerrer olusmaz. */
    @Transactional
    public AccrualGenerationResult uret(String donemRaw) {
        return calistir(parseDonem(donemRaw), true);
    }

    /** Donem icin ne uretilecegini hesaplar ama KAYDETMEZ (onizleme). */
    @Transactional(readOnly = true)
    public AccrualGenerationResult onizle(String donemRaw) {
        return calistir(parseDonem(donemRaw), false);
    }

    /**
     * Ortak cekirdek: uygun kayitlar uzerinde gezer; mevcut tahakkugu olanlari atlar, olmayanlar icin
     * (persist=true ise) Accrual olusturur. persist=false ise hicbir satir yazilmaz (onizleme).
     */
    private AccrualGenerationResult calistir(String donem, boolean persist) {
        List<OzetKalemi> ozet = new ArrayList<>();
        BigDecimal toplamTutar = BigDecimal.ZERO;
        int atlanan = 0;

        for (Enrollment kayit : enrollmentRepository.findAktifAidatliKayitlar()) {
            // Dalga E: donemlik kayit donem ucretini kayitta tek kalem odedi; aylik aidat URETILMEZ.
            if (kayit.getOdemePlani() == OdemePlani.DONEMLIK) {
                continue;
            }
            Student ogrenci = kayit.getOgrenci();
            Group grup = kayit.getGrup();
            if (accrualRepository.existsByOgrenciAndGrupAndDonem(ogrenci.getId(), grup.getId(), donem)) {
                atlanan++;
                continue;
            }
            BigDecimal brut = grup.getAylikAidat().setScale(2, RoundingMode.HALF_UP);
            // Dalga D: ogrenciye ozel indirim donem basinda gecerliyse net = brut - indirim.
            IndirimSonucu indirim = indirimService.hesapla(ogrenci.getId(), grup.getId(),
                    YearMonth.parse(donem).atDay(1), brut);
            BigDecimal tutar = indirim.net();
            if (persist) {
                Accrual accrual = Accrual.create();
                accrual.setOgrenci(ogrenci);
                accrual.setGrup(grup);
                accrual.setDonem(donem);
                accrual.setTutar(tutar);
                accrual.setAciklama("Otomatik aylık tahakkuk - " + donem
                        + (indirim.var() ? " (indirim: " + indirim.aciklama() + ")" : ""));
                if (indirim.var()) {
                    accrual.setBrutTutar(indirim.brut());
                    accrual.setIndirimTutar(indirim.indirim());
                    accrual.setIndirimAciklama(indirim.aciklama());
                }
                accrualRepository.save(accrual);
            }
            ozet.add(new OzetKalemi(ogrenci.getId(), grup.getId(), tutar, indirim.brut(),
                    indirim.var() ? indirim.indirim() : null, indirim.aciklama()));
            toplamTutar = toplamTutar.add(tutar);
        }

        List<DenemeOgrenci> deneme = enrollmentRepository.findDenemeAidatliKayitlar().stream()
                .map(k -> new DenemeOgrenci(k.getOgrenci().getId(), k.getOgrenci().getAd(),
                        k.getOgrenci().getSoyad(), k.getGrup().getId(), k.getGrup().getAd()))
                .toList();

        if (persist) {
            // Dalga E: ayin kredileri (0 TL paket, ders sayisi kadar) — aidatla ayni tetikleyici, tek yer.
            krediService.aylikKredileriUret(donem);
        }
        return new AccrualGenerationResult(
                donem, ozet.size(), atlanan, toplamTutar.setScale(2, RoundingMode.HALF_UP), ozet, deneme);
    }

    /** "YYYY-MM" donemini parse/normalize eder; gecersizse 400 VALIDATION_ERROR. */
    private String parseDonem(String donem) {
        try {
            return YearMonth.parse(donem).toString();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("Geçersiz dönem formatı (YYYY-MM)");
        }
    }
}
