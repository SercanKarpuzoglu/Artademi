package com.artademi.report;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.report.dto.FinancialSummaryResponse;
import com.artademi.report.dto.GroupOccupancyRow;
import com.artademi.report.dto.StudentBalanceRow;
import com.artademi.report.dto.TeacherPayoutsResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import com.artademi.report.dto.AttendanceReportResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rapor (RAPOR) ucu — SALT OKUNUR aggregate'ler. Hicbir kayit OLUSTURMAZ/DEGISTIRMEZ; yeni entity,
 * migration, web/mobil YOK. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur ve her sorgu global tenant filtresine tabidir (bkz. multi-tenancy / keycloak-auth). Her rapor
 * yalnizca aktif tenant'in verisini gorur.
 *
 * <p>Yetki metot duzeyinde uygulanir (PARA HASSAS):
 * <ul>
 *   <li>financial-summary, teacher-payouts: yalnizca ADMIN.</li>
 *   <li>student-balances: ADMIN / FRONTDESK_ACCOUNTING.</li>
 *   <li>group-occupancy: ADMIN / FRONTDESK_ACCOUNTING / FRONTDESK.</li>
 * </ul>
 * TEACHER hicbirine erisemez (403).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** Aylik finansal ozet (gelir/gider/net). Yalnizca ADMIN. Gecersiz donem -> 400. */
    @GetMapping("/financial-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FinancialSummaryResponse> financialSummary(@RequestParam String donem) {
        return ApiResponse.ok(service.financialSummary(donem));
    }

    /**
     * Ogrenci bakiyeleri (bakiye DESC, sayfali). ADMIN / FRONTDESK_ACCOUNTING. {@code sadeceBorclu}
     * true ise yalnizca borclu (bakiye &gt; 0) ogrenciler.
     */
    @GetMapping("/student-balances")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<StudentBalanceRow>> studentBalances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean sadeceBorclu) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StudentBalanceRow> result = service.studentBalances(sadeceBorclu, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }

    /** Ogretmen hakedisleri dokumu + toplam. Yalnizca ADMIN. Gecersiz donem -> 400. */
    @GetMapping("/teacher-payouts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TeacherPayoutsResponse> teacherPayouts(@RequestParam String donem) {
        return ApiResponse.ok(service.teacherPayouts(donem));
    }

    /**
     * Grup doluluk (AKTIF kayit sayilari). ADMIN / FRONTDESK_ACCOUNTING / FRONTDESK. {@code aktifMi}
     * opsiyonel; doluysa yalnizca o aktiflik durumundaki gruplar.
     */
    @GetMapping("/group-occupancy")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING','FRONTDESK')")
    public ApiResponse<List<GroupOccupancyRow>> groupOccupancy(
            @RequestParam(required = false) Boolean aktifMi) {
        return ApiResponse.ok(service.groupOccupancy(aktifMi));
    }

    /**
     * DEVAMSIZLIK RAPORU. Tarih araligi zorunlu; {@code grupId} verilirse tek gruba daralir.
     * Yetki: ADMIN + FRONTDESK + ACCOUNTING — devamsizlik parasal bir bilgi degildir, on buro
     * da veliyi arayabilmek icin gormeli.
     */
    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<AttendanceReportResponse> attendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baslangic,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bitis,
            @RequestParam(required = false) Long grupId) {
        return ApiResponse.ok(service.attendanceReport(baslangic, bitis, grupId));
    }

    /**
     * Devamsizlik raporunun CSV hali (Excel icin). Muhasebe/yonetim raporlari cogu zaman
     * Excel'de islenir; ekrandan elle kopyalamak hataya acik.
     */
    @GetMapping("/attendance.csv")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ResponseEntity<byte[]> attendanceCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baslangic,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bitis,
            @RequestParam(required = false) Long grupId) throws java.io.IOException {
        AttendanceReportResponse rapor = service.attendanceReport(baslangic, bitis, grupId);
        com.artademi.export.CsvYazici c = new com.artademi.export.CsvYazici();
        c.satir("Öğrenci", "Toplam Ders", "Geldi", "Gelmedi", "İzinli", "Katılım Oranı (%)");
        rapor.satirlar().forEach(r -> c.satir(r.ogrenciAdSoyad(), r.toplamDers(), r.geldi(),
                r.gelmedi(), r.izinli(), r.katilimOrani()));

        String dosya = "devamsizlik_" + baslangic + "_" + bitis + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dosya + "\"")
                .body(c.baytlar());
    }
}
