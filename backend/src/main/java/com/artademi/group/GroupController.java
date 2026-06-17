package com.artademi.group;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.group.dto.CreateGroupRequest;
import com.artademi.group.dto.GroupResponse;
import com.artademi.group.dto.UpdateActiveRequest;
import com.artademi.group.dto.UpdateGroupRequest;
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
 * Grup tanim ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
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
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    /** Yeni grup olustur (aktif true), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek grup (yoksa 404). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<GroupResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Grup guncelle (aktiflik degismez). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GroupResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Aktiflik degisikligi (pasiflestirme dahil). */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GroupResponse> changeActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return ApiResponse.ok(service.changeActive(id, request.aktif()));
    }

    /** Filtreli/sayfali liste: ?tip=&aktif=&bransId=&ogretmenId=&salonId=&q=&page=0&size=20. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<GroupResponse>> list(
            @RequestParam(required = false) GrupTipi tip,
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) Long bransId,
            @RequestParam(required = false) Long ogretmenId,
            @RequestParam(required = false) Long salonId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ad").ascending());
        Page<GroupResponse> result = service.search(tip, aktif, bransId, ogretmenId, salonId, q, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
