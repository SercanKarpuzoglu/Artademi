package com.artademi.report;

import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.ExpenseRepository;
import com.artademi.finance.PaymentRepository;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.group.GroupSpecifications;
import com.artademi.inventory.SaleRepository;
import com.artademi.payout.Payout;
import com.artademi.payout.PayoutRepository;
import com.artademi.report.dto.FinancialSummaryResponse;
import com.artademi.report.dto.GroupOccupancyRow;
import com.artademi.report.dto.StudentBalanceRow;
import com.artademi.report.dto.TeacherPayoutsResponse;
import com.artademi.report.dto.TeacherPayoutsResponse.TeacherPayoutRow;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rapor (RAPOR) is kurallari — SALT OKUNUR aggregate'ler. Hicbir kayit OLUSTURMAZ/DEGISTIRMEZ.
 * Tum metotlar {@code @Transactional(readOnly = true)} oldugundan cagrildiginda global tenant filtresi
 * aktif oturumda calisir; her rapor yalnizca aktif tenant'in verisini gorur (bkz. multi-tenancy).
 *
 * <p>PARA KURALI: tum parasal alanlar {@link BigDecimal}, scale 2, {@link RoundingMode#HALF_UP};
 * donus oncesi setScale uygulanir. Bos toplamlar repo'da COALESCE ile 0 doner.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int SCALE = 2;

    private final PaymentRepository paymentRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final PayoutRepository payoutRepository;
    private final AccrualRepository accrualRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final EnrollmentRepository enrollmentRepository;

    public ReportService(PaymentRepository paymentRepository, SaleRepository saleRepository,
            ExpenseRepository expenseRepository, PayoutRepository payoutRepository,
            AccrualRepository accrualRepository, StudentRepository studentRepository,
            GroupRepository groupRepository, EnrollmentRepository enrollmentRepository) {
        this.paymentRepository = paymentRepository;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.payoutRepository = payoutRepository;
        this.accrualRepository = accrualRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Aylik finansal ozet. {@code donem} "YYYY-MM" olmalidir (gecersizse 400). Gelir = tahsilat +
     * urun satis; gider = ofis gideri + hakedis; net = gelir - gider.
     */
    public FinancialSummaryResponse financialSummary(String donemRaw) {
        YearMonth ym = parseDonem(donemRaw);
        String donem = ym.toString();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal tahsilat = scale(paymentRepository.sumTutarByTarihAraligi(from, to));
        BigDecimal urunSatis = scale(saleRepository.sumToplamTutarByTarihAraligi(from, to));
        BigDecimal toplamGelir = scale(tahsilat.add(urunSatis));

        BigDecimal ofisGideri = scale(expenseRepository.sumTutarByTarihAraligi(from, to));
        BigDecimal hakedis = scale(payoutRepository.sumHesaplananByDonem(donem));
        BigDecimal toplamGider = scale(ofisGideri.add(hakedis));

        BigDecimal net = scale(toplamGelir.subtract(toplamGider));

        return new FinancialSummaryResponse(
                donem,
                new FinancialSummaryResponse.Gelir(tahsilat, urunSatis, toplamGelir),
                new FinancialSummaryResponse.Gider(ofisGideri, hakedis, toplamGider),
                net);
    }

    /**
     * Ogrenci bakiyeleri (tahakkuk - tahsilat), bakiye DESC sirali, sayfali. {@code sadeceBorclu}
     * true ise yalnizca bakiye &gt; 0 olanlar. Tum ogrenciler yuklenir; tahakkuk/tahsilat toplamlari
     * gruplu sorgularla (N+1 yok) cozulur ve bellekte birlestirilir.
     */
    public Page<StudentBalanceRow> studentBalances(boolean sadeceBorclu, Pageable pageable) {
        Map<Long, BigDecimal> accrualMap = toSumMap(accrualRepository.sumTutarGroupByOgrenci());
        Map<Long, BigDecimal> paymentMap = toSumMap(paymentRepository.sumTutarGroupByOgrenci());

        List<StudentBalanceRow> rows = new ArrayList<>();
        for (Student student : studentRepository.findAll()) {
            BigDecimal tahakkuk = scale(accrualMap.getOrDefault(student.getId(), BigDecimal.ZERO));
            BigDecimal odeme = scale(paymentMap.getOrDefault(student.getId(), BigDecimal.ZERO));
            BigDecimal bakiye = scale(tahakkuk.subtract(odeme));
            if (sadeceBorclu && bakiye.signum() <= 0) {
                continue;
            }
            rows.add(new StudentBalanceRow(student.getId(), student.getAd(), student.getSoyad(),
                    tahakkuk, odeme, bakiye));
        }

        rows.sort(Comparator.comparing(StudentBalanceRow::bakiye).reversed());

        int total = rows.size();
        int fromIndex = Math.min((int) pageable.getOffset(), total);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), total);
        List<StudentBalanceRow> content = rows.subList(fromIndex, toIndex);
        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Verilen donemdeki ogretmen hakedisleri dokumu + toplam. {@code donem} "YYYY-MM" olmalidir
     * (gecersizse 400).
     */
    public TeacherPayoutsResponse teacherPayouts(String donemRaw) {
        String donem = parseDonem(donemRaw).toString();

        List<TeacherPayoutRow> kalemler = new ArrayList<>();
        for (Payout p : payoutRepository.findByDonem(donem)) {
            kalemler.add(new TeacherPayoutRow(
                    p.getOgretmen().getId(),
                    p.getOgretmen().getAd(),
                    p.getOgretmen().getSoyad(),
                    p.getHakedisTipi(),
                    scale(p.getHesaplananTutar()),
                    p.getDurum()));
        }

        BigDecimal toplamHakedis = scale(payoutRepository.sumHesaplananByDonem(donem));
        return new TeacherPayoutsResponse(donem, toplamHakedis, kalemler);
    }

    /**
     * Grup doluluk: her grup icin AKTIF kayit sayisi. {@code aktifMi} doluysa yalnizca o aktiflik
     * durumundaki gruplar; null ise tum gruplar.
     */
    public List<GroupOccupancyRow> groupOccupancy(Boolean aktifMi) {
        Map<Long, Long> countMap = toCountMap(enrollmentRepository.countAktifGroupByGrup());

        Specification<Group> spec = GroupSpecifications.hasAktif(aktifMi);
        List<Group> groups = spec == null
                ? groupRepository.findAll()
                : groupRepository.findAll(spec);

        List<GroupOccupancyRow> rows = new ArrayList<>();
        for (Group g : groups) {
            String ogretmenAd = g.getOgretmen().getAd() + " " + g.getOgretmen().getSoyad();
            long aktifOgrenciSayisi = countMap.getOrDefault(g.getId(), 0L);
            rows.add(new GroupOccupancyRow(g.getId(), g.getAd(), g.getTip(), ogretmenAd,
                    aktifOgrenciSayisi));
        }
        return rows;
    }

    // --- Yardimcilar ---

    private static Map<Long, BigDecimal> toSumMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static YearMonth parseDonem(String donem) {
        try {
            return YearMonth.parse(donem);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("Geçersiz dönem formatı (YYYY-MM)");
        }
    }
}
