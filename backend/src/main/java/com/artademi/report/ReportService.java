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
import com.artademi.attendance.YoklamaDurumu;
import com.artademi.report.dto.AttendanceReportResponse;
import com.artademi.report.dto.AttendanceReportRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherRepository;
import com.artademi.schedule.Schedule;
import com.artademi.schedule.ScheduleRepository;
import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.kredi.KrediService;
import com.artademi.report.dto.TeacherQualityResponse;
import com.artademi.report.dto.TeacherQualityRow;

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
    private final com.artademi.attendance.AttendanceEntryRepository attendanceEntryRepository;

    private final TeacherRepository teacherRepository;
    private final ScheduleRepository scheduleRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final KrediService krediService;

    public ReportService(PaymentRepository paymentRepository, SaleRepository saleRepository,
            ExpenseRepository expenseRepository, PayoutRepository payoutRepository,
            AccrualRepository accrualRepository, StudentRepository studentRepository,
            GroupRepository groupRepository, EnrollmentRepository enrollmentRepository,
            com.artademi.attendance.AttendanceEntryRepository attendanceEntryRepository,
            TeacherRepository teacherRepository, ScheduleRepository scheduleRepository,
            AttendanceSessionRepository attendanceSessionRepository, KrediService krediService) {
        this.paymentRepository = paymentRepository;
        this.saleRepository = saleRepository;
        this.expenseRepository = expenseRepository;
        this.payoutRepository = payoutRepository;
        this.accrualRepository = accrualRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceEntryRepository = attendanceEntryRepository;
        this.teacherRepository = teacherRepository;
        this.scheduleRepository = scheduleRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.krediService = krediService;
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

    /**
     * DEVAMSIZLIK RAPORU — bir tarih araliginda ogrenci bazinda katilim ozeti.
     *
     * <p>Satirlar KATILIM ORANI ARTAN sirada doner: yoneticinin gormek istedigi once "en cok
     * devamsizlik yapan" ogrencidir. Alfabetik siralama bu raporu ise yaramaz hale getirirdi.
     *
     * <p>Payda olarak ogrencinin GERCEKTEN yoklamasi alinan ders sayisi kullanilir (kendi
     * satirlarinin toplami) — donemin ortasinda kaydolan ogrenci, katilmadigi eski derslerden
     * dolayi haksiz yere dusuk oranli gorunmesin.
     */
    public AttendanceReportResponse attendanceReport(LocalDate baslangic, LocalDate bitis,
            Long grupId) {
        if (bitis.isBefore(baslangic)) {
            throw new ValidationException("Bitiş tarihi başlangıçtan önce olamaz");
        }
        record Sayim(String adSoyad, long geldi, long gelmedi, long izinli) {
            long toplam() {
                return geldi + gelmedi + izinli;
            }
        }
        Map<Long, Sayim> ogrenciler = new LinkedHashMap<>();
        for (Object[] r : attendanceEntryRepository.katilimSayimlari(baslangic, bitis, grupId)) {
            Long id = (Long) r[0];
            String adSoyad = (r[1] + " " + r[2]).trim();
            YoklamaDurumu durum = (YoklamaDurumu) r[3];
            long adet = ((Number) r[4]).longValue();

            Sayim mevcut = ogrenciler.getOrDefault(id, new Sayim(adSoyad, 0, 0, 0));
            ogrenciler.put(id, switch (durum) {
                case GELDI -> new Sayim(adSoyad, mevcut.geldi() + adet, mevcut.gelmedi(),
                        mevcut.izinli());
                case GELMEDI -> new Sayim(adSoyad, mevcut.geldi(), mevcut.gelmedi() + adet,
                        mevcut.izinli());
                case IZINLI -> new Sayim(adSoyad, mevcut.geldi(), mevcut.gelmedi(),
                        mevcut.izinli() + adet);
            });
        }

        List<AttendanceReportRow> satirlar = ogrenciler.entrySet().stream()
                .map(e -> {
                    Sayim s = e.getValue();
                    BigDecimal oran = s.toplam() == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(s.geldi() * 100.0 / s.toplam())
                                    .setScale(2, RoundingMode.HALF_UP);
                    return new AttendanceReportRow(e.getKey(), s.adSoyad(), s.toplam(),
                            s.geldi(), s.gelmedi(), s.izinli(), oran);
                })
                .sorted(Comparator.comparing(AttendanceReportRow::katilimOrani)
                        .thenComparing(AttendanceReportRow::ogrenciAdSoyad))
                .toList();

        return new AttendanceReportResponse(baslangic, bitis,
                attendanceEntryRepository.oturumSayisi(baslangic, bitis, grupId), satirlar);
    }

    /**
     * EGITMEN KALITESI (Dalga F): her aktif egitmen icin yuk (aktif grup, ogrenci, haftalik ders saati) ve
     * tarih araliginda planlanan ders (program x takvim) vs alinan yoklama, kaydedilmemis oturum, katilim orani.
     * Satirlar katilim orani ARTAN — once dikkat gerektiren egitmen. Ogrencisi/oturumu olmayan egitmen de listelenir.
     */
    public TeacherQualityResponse teacherQuality(LocalDate baslangic, LocalDate bitis) {
        if (bitis.isBefore(baslangic)) {
            throw new ValidationException("Bitiş tarihi başlangıçtan önce olamaz");
        }
        Map<Long, Long> ogrenciSayilari = toCountMap(enrollmentRepository.countAktifGroupByGrup());
        Map<Long, Long> oturumlar = toCountMap(attendanceSessionRepository.oturumSayimlariOgretmen(baslangic, bitis));
        Map<Long, Long> kaydedilmemisler = toCountMap(attendanceSessionRepository.kaydedilmemisSayimlariOgretmen(baslangic, bitis));
        Map<Long, long[]> sayimlar = new HashMap<>(); // [geldi, gelmedi, izinli]
        for (Object[] r : attendanceEntryRepository.katilimSayimlariOgretmen(baslangic, bitis)) {
            long[] s = sayimlar.computeIfAbsent((Long) r[0], k -> new long[3]);
            s[((YoklamaDurumu) r[1]).ordinal()] += ((Number) r[2]).longValue();
        }
        // Egitmen -> aktif gruplari (program uzerinden: yalniz ders saati olan gruplar yuk uretir)
        Map<Long, List<Group>> gruplar = new HashMap<>();
        Map<Long, BigDecimal> haftalikSaat = new HashMap<>();
        for (Schedule s : scheduleRepository.findAktifHepsi()) {
            Group g = s.getGrup();
            if (g == null || g.getOgretmen() == null) {
                continue;
            }
            Long oid = g.getOgretmen().getId();
            List<Group> liste = gruplar.computeIfAbsent(oid, k -> new ArrayList<>());
            if (liste.stream().noneMatch(x -> x.getId().equals(g.getId()))) {
                liste.add(g);
            }
            long dakika = java.time.Duration.between(s.getBaslangicSaati(), s.getBitisSaati()).toMinutes();
            haftalikSaat.merge(oid, BigDecimal.valueOf(dakika).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP), BigDecimal::add);
        }
        List<TeacherQualityRow> satirlar = new ArrayList<>();
        for (Teacher t : teacherRepository.findAll()) {
            if (!t.isAktif()) {
                continue;
            }
            List<Group> tg = gruplar.getOrDefault(t.getId(), List.of());
            long ogrenci = tg.stream().mapToLong(g -> ogrenciSayilari.getOrDefault(g.getId(), 0L)).sum();
            int planlanan = tg.stream().mapToInt(g -> krediService.dersSayisi(g.getId(), baslangic, bitis)).sum();
            long oturum = oturumlar.getOrDefault(t.getId(), 0L);
            long[] s = sayimlar.getOrDefault(t.getId(), new long[3]);
            long payda = s[0] + s[1];
            BigDecimal oran = payda == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(s[0] * 100.0 / payda).setScale(2, RoundingMode.HALF_UP);
            satirlar.add(new TeacherQualityRow(t.getId(), t.getAd(), t.getSoyad(), tg.size(), ogrenci,
                    haftalikSaat.getOrDefault(t.getId(), BigDecimal.ZERO.setScale(2)), planlanan, oturum,
                    Math.max(0, planlanan - oturum), kaydedilmemisler.getOrDefault(t.getId(), 0L),
                    s[0], s[1], s[2], oran));
        }
        satirlar.sort(Comparator.comparing(TeacherQualityRow::katilimOrani).thenComparing(TeacherQualityRow::ad));
        return new TeacherQualityResponse(baslangic, bitis, satirlar);
    }
}
