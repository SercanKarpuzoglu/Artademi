package com.artademi.attendance;

import com.artademi.attendance.dto.CreateSessionRequest;
import com.artademi.attendance.dto.SessionResponse;
import com.artademi.attendance.dto.UpdateEntryItem;
import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yoklama oturumu ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki @PreAuthorize ile rol KAPISI olarak ayrilir: yazma (POST/PUT) ADMIN/FRONTDESK/TEACHER;
 * okuma (GET) ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING/TEACHER. TEACHER'in yalnizca KENDI gruplarina
 * erisme daraltmasi servis/guard katmaninda ({@link AttendanceAccessGuard}) yapilir, @PreAuthorize'da
 * DEGIL.
 *
 * <p>DELETE YOK (oturum = yoklama alinmis demektir).
 */
@RestController
@RequestMapping("/api/attendance-sessions")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    /** Yeni yoklama oturumu olustur (aktif kayitli ogrenciler GELMEDI uretilir), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','TEACHER')")
    public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek oturum + girisleri (yoksa 404). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING','TEACHER')")
    public ApiResponse<SessionResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Toplu durum guncellemesi; govde cıplak dizi: {@code [{ogrenciId, durum}, ...]}. */
    @PutMapping("/{id}/entries")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','TEACHER')")
    public ApiResponse<SessionResponse> updateEntries(
            @PathVariable Long id,
            @Valid @RequestBody List<UpdateEntryItem> entries) {
        return ApiResponse.ok(service.updateEntries(id, entries));
    }

    /** Filtreli/sayfali liste: ?grupId=&tarih=&page=0&size=20. TEACHER kendi gruplariyla sinirli. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING','TEACHER')")
    public ApiResponse<List<SessionResponse>> list(
            @RequestParam(required = false) Long grupId,
            @RequestParam(required = false) LocalDate tarih,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("tarih").descending());
        Page<SessionResponse> result = service.search(grupId, tarih, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
