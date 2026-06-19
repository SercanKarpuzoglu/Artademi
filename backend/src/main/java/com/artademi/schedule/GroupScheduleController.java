package com.artademi.schedule;

import com.artademi.common.ApiResponse;
import com.artademi.schedule.dto.ScheduleResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir grubun haftalik programini donen yardimci uc (GET /api/groups/{groupId}/schedules).
 *
 * <p>GroupController'a DOKUNMAMAK icin program paketinde ayri bir controller olarak tanimlanir.
 * grupId tenant-guvenli ({@code groupRepository.findScopedById} -> serviste) cozulur; baska tenant'a
 * ait/yok ise 404. Sonuc gun + baslangic saati sirali bir LISTE'dir (sayfalanmaz).
 *
 * <p>Yetki: okuma rolleri (ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING). TEACHER 403.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/schedules")
public class GroupScheduleController {

    private final ScheduleService service;

    public GroupScheduleController(ScheduleService service) {
        this.service = service;
    }

    /** Grubun haftalik programi (gun, ardindan baslangic saati sirali). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<ScheduleResponse>> list(@PathVariable Long groupId) {
        return ApiResponse.ok(service.listByGroup(groupId));
    }
}
