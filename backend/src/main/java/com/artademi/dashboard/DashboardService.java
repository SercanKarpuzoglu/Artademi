package com.artademi.dashboard;

import com.artademi.attendance.AttendanceService;
import com.artademi.attendance.YoklamaDurumu;
import com.artademi.attendance.dto.SessionResponse;
import com.artademi.common.tenant.TenantContext;
import com.artademi.dashboard.dto.AccountingDashboard;
import com.artademi.dashboard.dto.AdminDashboard;
import com.artademi.dashboard.dto.DashboardData;
import com.artademi.dashboard.dto.DersOzet;
import com.artademi.dashboard.dto.FrontdeskDashboard;
import com.artademi.dashboard.dto.OdemeOzet;
import com.artademi.dashboard.dto.OgrenciOzet;
import com.artademi.dashboard.dto.TeacherDashboard;
import com.artademi.finance.PaymentService;
import com.artademi.finance.dto.PaymentResponse;
import com.artademi.group.GrupTipi;
import com.artademi.group.GroupService;
import com.artademi.group.dto.GroupResponse;
import com.artademi.report.ReportService;
import com.artademi.report.dto.FinancialSummaryResponse;
import com.artademi.report.dto.GroupOccupancyRow;
import com.artademi.report.dto.StudentBalanceRow;
import com.artademi.schedule.HaftaGunu;
import com.artademi.schedule.ScheduleService;
import com.artademi.schedule.dto.ScheduleResponse;
import com.artademi.student.StudentStatus;
import com.artademi.student.StudentService;
import com.artademi.student.dto.StudentResponse;
import com.artademi.teacher.CurrentTeacherResolver;
import com.artademi.teacher.Teacher;
import com.artademi.user.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Genel Bakis" panel ozeti (read-only). Hicbir yeni hesap mantigi yazmaz; mevcut servisleri
 * (report/finance/student/group/schedule/attendance/platform) CAGIRIR ve role gore izinli alanlari
 * birlestirir.
 *
 * <p><b>Guvenlik:</b> rol token'dan ({@link CurrentUser#realmRoles()}) okunur ve EN YETKILI role gore
 * (ADMIN &gt; FRONTDESK_ACCOUNTING &gt; FRONTDESK &gt; TEACHER) farkli SOMUT DTO uretilir. Parasal
 * alanlar tip-duzeyinde filtrelidir: FRONTDESK/TEACHER DTO'larinda parasal alan HIC YOKTUR. Uygun
 * is rolu yoksa 403 (super.admin zaten tenant'siz oldugundan /api/** -> 400 alir, buraya gelmez).
 */
@Service
public class DashboardService {

    private static final int TREND_MONTHS = 6;
    private static final int RECENT_LIMIT = 5;
    private static final int DERS_LIMIT = 200;

    private final ReportService reportService;
    private final PaymentService paymentService;
    private final StudentService studentService;
    private final GroupService groupService;
    private final ScheduleService scheduleService;
    private final AttendanceService attendanceService;
    private final CurrentUser currentUser;
    private final CurrentTeacherResolver currentTeacherResolver;
    private final com.artademi.platform.SubscriptionService subscriptionService;

    public DashboardService(ReportService reportService, PaymentService paymentService,
            StudentService studentService, GroupService groupService, ScheduleService scheduleService,
            AttendanceService attendanceService, CurrentUser currentUser,
            CurrentTeacherResolver currentTeacherResolver,
            com.artademi.platform.SubscriptionService subscriptionService) {
        this.reportService = reportService;
        this.paymentService = paymentService;
        this.studentService = studentService;
        this.groupService = groupService;
        this.scheduleService = scheduleService;
        this.attendanceService = attendanceService;
        this.currentUser = currentUser;
        this.currentTeacherResolver = currentTeacherResolver;
        this.subscriptionService = subscriptionService;
    }

    /** Token rolune gore (en yetkili) panel verisini uretir. Uygun is rolu yoksa 403. */
    @Transactional(readOnly = true)
    public DashboardData build() {
        List<String> roller = currentUser.realmRoles();
        if (roller.contains("ADMIN")) {
            return buildAdmin();
        }
        if (roller.contains("FRONTDESK_ACCOUNTING")) {
            return buildAccounting();
        }
        if (roller.contains("FRONTDESK")) {
            return buildFrontdesk();
        }
        if (roller.contains("TEACHER")) {
            return buildTeacher();
        }
        throw new AccessDeniedException("Panel için uygun rol yok");
    }

    // ===================================================================== ADMIN

    private AdminDashboard buildAdmin() {
        List<AdminDashboard.Trend> trend = new java.util.ArrayList<>();
        FinancialSummaryResponse current = null;
        YearMonth ym = YearMonth.now();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            FinancialSummaryResponse fs = reportService.financialSummary(ym.minusMonths(i).toString());
            if (i == 0) {
                current = fs;
            }
            trend.add(new AdminDashboard.Trend(
                    fs.donem(), fs.gelir().tahsilat(), fs.gider().toplamGider(), fs.net()));
        }

        AdminDashboard.Sayilar sayilar = new AdminDashboard.Sayilar(
                aktifOgrenciSayisi(),
                aktifGrupSayisi(),
                current.gelir().tahsilat(),
                current.gider().toplamGider(),
                current.net(),
                bekleyenBorcToplam());

        return new AdminDashboard(
                "ADMIN",
                sayilar,
                trend,
                sonOdemeler(),
                sonOgrenciler(),
                bugunDersler(),
                subscriptionWarning());
    }

    // =========================================================== FRONTDESK_ACCOUNTING

    private AccountingDashboard buildAccounting() {
        List<AccountingDashboard.Trend> trend = new java.util.ArrayList<>();
        FinancialSummaryResponse current = null;
        YearMonth ym = YearMonth.now();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            FinancialSummaryResponse fs = reportService.financialSummary(ym.minusMonths(i).toString());
            if (i == 0) {
                current = fs;
            }
            // Yalnizca tahsilat (gider/net admin'e ozel).
            trend.add(new AccountingDashboard.Trend(fs.donem(), fs.gelir().tahsilat()));
        }

        AccountingDashboard.Sayilar sayilar = new AccountingDashboard.Sayilar(
                aktifOgrenciSayisi(),
                aktifGrupSayisi(),
                current.gelir().tahsilat(),
                bekleyenBorcToplam());

        return new AccountingDashboard(
                "FRONTDESK_ACCOUNTING",
                sayilar,
                trend,
                sonOdemeler(),
                sonOgrenciler(),
                bugunDersler());
    }

    // ================================================================= FRONTDESK (para YOK)

    private FrontdeskDashboard buildFrontdesk() {
        return new FrontdeskDashboard(
                "FRONTDESK",
                new FrontdeskDashboard.Sayilar(aktifOgrenciSayisi(), aktifGrupSayisi()),
                bugunDersler(),
                sonOgrenciler());
    }

    // ================================================================= TEACHER (sadece kendi)

    private TeacherDashboard buildTeacher() {
        Long ogretmenId = currentTeacherResolver.current().map(Teacher::getId).orElse(null);
        // Ogretmen kaydi yoksa: bos panel (attendance/group servisleri TEACHER icin ogretmen
        // eslesmesi bekler — gereksiz 403 atmamak icin burada kisa devre).
        if (ogretmenId == null) {
            return new TeacherDashboard("TEACHER", List.of(), List.of(), List.of());
        }
        List<GroupResponse> gruplar = groupService.mine(ogretmenId);

        // Grup -> aktif ogrenci sayisi (groupOccupancy yeniden kullanilir; yeni hesap yok).
        Map<Long, Long> ogrenciSayilari = reportService.groupOccupancy(null).stream()
                .collect(Collectors.toMap(GroupOccupancyRow::grupId, GroupOccupancyRow::aktifOgrenciSayisi));

        List<TeacherDashboard.Grup> kendiGruplar = gruplar.stream()
                .map(g -> new TeacherDashboard.Grup(
                        g.id(), g.ad(), g.tip(), ogrenciSayilari.getOrDefault(g.id(), 0L)))
                .toList();

        Set<Long> grupIds = gruplar.stream().map(GroupResponse::id).collect(Collectors.toSet());
        List<DersOzet> bugun = bugunDerslerTumu().stream()
                .filter(s -> grupIds.contains(s.grup().id()))
                .map(DashboardService::toDers)
                .toList();

        // Son yoklamalar: AttendanceService.search TEACHER icin otomatik kendi gruplariyla daralir.
        List<TeacherDashboard.Yoklama> sonYoklamalar = attendanceService
                .search(null, null, PageRequest.of(0, RECENT_LIMIT, Sort.by("tarih").descending()))
                .getContent().stream()
                .map(DashboardService::toYoklama)
                .toList();

        return new TeacherDashboard("TEACHER", kendiGruplar, bugun, sonYoklamalar);
    }

    // ===================================================================== ortak yardimcilar

    private long aktifOgrenciSayisi() {
        return studentService.search(StudentStatus.AKTIF, null, PageRequest.of(0, 1)).getTotalElements();
    }

    private long aktifGrupSayisi() {
        return groupService.search(null, true, null, null, null, null, PageRequest.of(0, 1))
                .getTotalElements();
    }

    private BigDecimal bekleyenBorcToplam() {
        return reportService.studentBalances(true, PageRequest.of(0, 100_000)).getContent().stream()
                .map(StudentBalanceRow::bakiye)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<OdemeOzet> sonOdemeler() {
        return paymentService
                .search(null, null, null, null, PageRequest.of(0, RECENT_LIMIT, Sort.by("id").descending()))
                .getContent().stream()
                .map(p -> new OdemeOzet(
                        p.ogrenci().ad() + " " + p.ogrenci().soyad(),
                        p.tutar(), p.odemeTarihi(), p.odemeYontemi()))
                .toList();
    }

    private List<OgrenciOzet> sonOgrenciler() {
        return studentService
                .search(null, null, PageRequest.of(0, RECENT_LIMIT, Sort.by("olusturulmaTarihi").descending()))
                .getContent().stream()
                .map(s -> new OgrenciOzet(s.ad(), s.soyad(), s.status(), s.olusturulmaTarihi()))
                .toList();
    }

    private List<DersOzet> bugunDersler() {
        return bugunDerslerTumu().stream().map(DashboardService::toDers).toList();
    }

    /** Bugunun gunune denk gelen TUM aktif programlar (rol bazli filtre cagirana birakilir). */
    private List<ScheduleResponse> bugunDerslerTumu() {
        HaftaGunu gun = bugunGun();
        return scheduleService
                .search(null, gun, true, PageRequest.of(0, DERS_LIMIT, Sort.by("baslangicSaati").ascending()))
                .getContent();
    }

    private com.artademi.platform.dto.SubscriptionWarning subscriptionWarning() {
        UUID tenantId = TenantContext.get();
        return tenantId == null ? null : subscriptionService.warningFor(tenantId);
    }

    private static DersOzet toDers(ScheduleResponse s) {
        return new DersOzet(
                s.grup().ad(),
                s.baslangicSaati(),
                s.bitisSaati(),
                s.salon() == null ? null : s.salon().ad());
    }

    private static TeacherDashboard.Yoklama toYoklama(SessionResponse s) {
        long toplam = s.entries().size();
        long gelen = s.entries().stream()
                .filter(e -> e.durum() == YoklamaDurumu.GELDI)
                .count();
        return new TeacherDashboard.Yoklama(s.grup().ad(), s.tarih(), gelen, toplam);
    }

    private static HaftaGunu bugunGun() {
        return switch (LocalDate.now().getDayOfWeek()) {
            case MONDAY -> HaftaGunu.PAZARTESI;
            case TUESDAY -> HaftaGunu.SALI;
            case WEDNESDAY -> HaftaGunu.CARSAMBA;
            case THURSDAY -> HaftaGunu.PERSEMBE;
            case FRIDAY -> HaftaGunu.CUMA;
            case SATURDAY -> HaftaGunu.CUMARTESI;
            case SUNDAY -> HaftaGunu.PAZAR;
        };
    }
}
