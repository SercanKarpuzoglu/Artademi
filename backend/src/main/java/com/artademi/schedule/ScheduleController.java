package com.artademi.schedule;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.schedule.dto.CreateScheduleRequest;
import com.artademi.schedule.dto.ScheduleResponse;
import com.artademi.schedule.dto.UpdateActiveRequest;
import com.artademi.schedule.dto.UpdateScheduleRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Program (haftalik ders saati) tanim ucu. Ince controller: dogrular, servisi cagirir, ApiResponse
 * doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki METOT duzeyinde ayrilir (yazma ve okuma FARKLI roller): yazma (POST/PUT/PATCH) yalnizca
 * ADMIN; okuma (GET) ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING. TEACHER hicbirine erisemez (403).
 *
 * <p>DELETE YOK: silme yerine PATCH /{id}/active ile pasiflestirilir (veri korunur).
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    /** Yeni program olustur (aktif true), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek program (yoksa 404). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<ScheduleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Program guncelle (aktiflik degismez); cakisma kontrolu kendi kaydi haric uygulanir. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Aktiflik degisikligi (pasiflestirme dahil). */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ScheduleResponse> changeActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return ApiResponse.ok(service.changeActive(id, request.aktif()));
    }

    /** Filtreli/sayfali liste: ?grupId=&gun=&aktif=&page=0&size=20. Siralama: gun, sonra baslangic. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<ScheduleResponse>> list(
            @RequestParam(required = false) Long grupId,
            @RequestParam(required = false) HaftaGunu gun,
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("gun").ascending().and(Sort.by("baslangicSaati").ascending()));
        Page<ScheduleResponse> result = service.search(grupId, gun, aktif, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
