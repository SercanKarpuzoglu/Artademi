package com.artademi.finance;

import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.dto.AccrualGenerationResult;
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
 * <p>PARA KURALI: toplam ve kalem tutarlari {@link BigDecimal}, scale 2, {@link RoundingMode#HALF_UP}.
 */
@Service
public class AccrualGenerationService {

    private final EnrollmentRepository enrollmentRepository;
    private final AccrualRepository accrualRepository;

    public AccrualGenerationService(EnrollmentRepository enrollmentRepository,
            AccrualRepository accrualRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.accrualRepository = accrualRepository;
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
            Student ogrenci = kayit.getOgrenci();
            Group grup = kayit.getGrup();
            if (accrualRepository.existsByOgrenciAndGrupAndDonem(ogrenci.getId(), grup.getId(), donem)) {
                atlanan++;
                continue;
            }
            BigDecimal tutar = grup.getAylikAidat().setScale(2, RoundingMode.HALF_UP);
            if (persist) {
                Accrual accrual = Accrual.create();
                accrual.setOgrenci(ogrenci);
                accrual.setGrup(grup);
                accrual.setDonem(donem);
                accrual.setTutar(tutar);
                accrual.setAciklama("Otomatik aylık tahakkuk - " + donem);
                accrualRepository.save(accrual);
            }
            ozet.add(new OzetKalemi(ogrenci.getId(), grup.getId(), tutar));
            toplamTutar = toplamTutar.add(tutar);
        }

        return new AccrualGenerationResult(
                donem, ozet.size(), atlanan, toplamTutar.setScale(2, RoundingMode.HALF_UP), ozet);
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
