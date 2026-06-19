package com.artademi.attendance;

import com.artademi.attendance.dto.SessionResponse;
import com.artademi.common.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir grubun yoklama oturumlarini donen yardimci uc
 * (GET /api/groups/{groupId}/attendance-sessions?from=&to=).
 *
 * <p>GroupController'a DOKUNMAMAK icin yoklama paketinde ayri bir controller olarak tanimlanir.
 * grupId tenant-guvenli ({@code groupRepository.findScopedById} -> serviste) cozulur; baska tenant'a
 * ait/yok ise 404. TEACHER yalnizca kendi grubuna erisebilir (403). Sonuc tarihe gore artan sirali
 * bir LISTE'dir (sayfalanmaz).
 *
 * <p>{@code from}/{@code to} opsiyonel (ISO yyyy-MM-dd); verilen sinir uygulanir.
 *
 * <p>Yetki: okuma rolleri (ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING/TEACHER); TEACHER daraltmasi guard'da.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/attendance-sessions")
public class GroupAttendanceController {

    private final AttendanceService service;

    public GroupAttendanceController(AttendanceService service) {
        this.service = service;
    }

    /** Grubun [from, to] araligindaki oturumlari (tarihe gore artan sirali). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING','TEACHER')")
    public ApiResponse<List<SessionResponse>> list(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.listByGroup(groupId, from, to));
    }
}
