package com.artademi.room;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.room.dto.CreateRoomRequest;
import com.artademi.room.dto.RoomResponse;
import com.artademi.room.dto.UpdateActiveRequest;
import com.artademi.room.dto.UpdateRoomRequest;
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
 * Salon tanim ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id}
 * claim'inden konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki METOT duzeyinde ayrilir (yazma ve okuma FARKLI roller):
 * yazma (POST/PUT/PATCH) yalnizca ADMIN; okuma (GET) ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING.
 * TEACHER hicbirine erisemez (403).
 *
 * <p>DELETE YOK: silme yerine PATCH /{id}/active ile pasiflestirilir (veri korunur).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    /** Yeni salon olustur (aktif true), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek salon (yoksa 404). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<RoomResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Salon guncelle (aktiflik degismez). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Aktiflik degisikligi (pasiflestirme dahil). */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> changeActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return ApiResponse.ok(service.changeActive(id, request.aktif()));
    }

    /** Filtreli/sayfali liste: ?aktif=true&q=ara&page=0&size=20 (aktif, q opsiyonel). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<RoomResponse>> list(
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ad").ascending());
        Page<RoomResponse> result = service.search(aktif, q, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
