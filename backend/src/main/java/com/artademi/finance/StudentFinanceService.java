package com.artademi.finance;

import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.dto.AccrualResponse;
import com.artademi.finance.dto.BalanceResponse;
import com.artademi.finance.dto.FinanceSummaryResponse;
import com.artademi.finance.dto.PaymentResponse;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ogrenci finans ozeti/bakiye is kurallari. {@code @Transactional} oldugundan global tenant filtresi
 * aktif oturumda calisir.
 *
 * <p>Ogrenci once {@code findScopedById} ile tenant-guvenli cozulur; bulunamazsa (baska tenant'a ait
 * VEYA yok) -> 404 (sizinti yok).
 *
 * <p>PARA KURALI (KRITIK): bakiye = SUM(accrual.tutar) - SUM(payment.tutar), tum islem {@link BigDecimal}
 * ile yapilir; sonuc {@code .setScale(2, HALF_UP)} ile scale 2'ye sabitlenir. Pozitif bakiye = borc.
 * Asla double/float kullanilmaz.
 */
@Service
public class StudentFinanceService {

    private final StudentRepository studentRepository;
    private final AccrualRepository accrualRepository;
    private final PaymentRepository paymentRepository;

    public StudentFinanceService(StudentRepository studentRepository,
            AccrualRepository accrualRepository, PaymentRepository paymentRepository) {
        this.studentRepository = studentRepository;
        this.accrualRepository = accrualRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Ogrenci bakiyesi (toplam tahakkuk, toplam odeme, bakiye); hepsi scale 2. */
    @Transactional(readOnly = true)
    public BalanceResponse balance(Long ogrenciId) {
        Student ogrenci = resolveStudent(ogrenciId);
        BigDecimal toplamTahakkuk = scaled(accrualRepository.sumTutarByOgrenci(ogrenci.getId()));
        BigDecimal toplamOdeme = scaled(paymentRepository.sumTutarByOgrenci(ogrenci.getId()));
        BigDecimal bakiye = scaled(toplamTahakkuk.subtract(toplamOdeme));
        return new BalanceResponse(ogrenci.getId(), toplamTahakkuk, toplamOdeme, bakiye);
    }

    /** Ogrenci finans ozeti: tahakkuk + tahsilat listeleri ve guncel bakiye. */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary(Long ogrenciId) {
        Student ogrenci = resolveStudent(ogrenciId);
        List<AccrualResponse> tahakkuklar = accrualRepository.findByOgrenci(ogrenci.getId()).stream()
                .map(AccrualResponse::from)
                .toList();
        List<PaymentResponse> odemeler = paymentRepository.findByOgrenci(ogrenci.getId()).stream()
                .map(PaymentResponse::from)
                .toList();
        BigDecimal toplamTahakkuk = scaled(accrualRepository.sumTutarByOgrenci(ogrenci.getId()));
        BigDecimal toplamOdeme = scaled(paymentRepository.sumTutarByOgrenci(ogrenci.getId()));
        BigDecimal bakiye = scaled(toplamTahakkuk.subtract(toplamOdeme));
        return new FinanceSummaryResponse(ogrenci.getId(), tahakkuklar, odemeler, bakiye);
    }

    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    /** BigDecimal'i scale 2'ye (HALF_UP) sabitler; null -> 0.00. */
    private static BigDecimal scaled(BigDecimal value) {
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
