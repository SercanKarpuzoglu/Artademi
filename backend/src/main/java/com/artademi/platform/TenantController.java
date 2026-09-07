package com.artademi.platform;

import com.artademi.common.ApiResponse;
import com.artademi.platform.dto.TenantResponse;
import com.artademi.platform.dto.BasvuruSlugRequest;
import com.artademi.platform.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant ucu. Kullanici HER ZAMAN kendi tenant'ini gorur (id parametresi YOK; TenantContext'ten
 * gelir). Okuma her role acik; ad guncelleme yalnizca ADMIN.
 */
@RestController
@RequestMapping("/api/tenant")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    /** Oturum sahibinin kendi tenant bilgisi. */
    @GetMapping
    public ApiResponse<TenantResponse> current() {
        return ApiResponse.ok(service.current());
    }

    /** Kendi tenant'inin adini gunceller (SADECE ADMIN). */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TenantResponse> update(@Valid @RequestBody UpdateTenantRequest request) {
        return ApiResponse.ok(service.updateName(request));
    }

    /**
     * Public on kayit baglanti adini belirler/kaldirir. Yalnizca ADMIN: bu baglanti
     * kurumun disariya acilan yuzudur.
     */
    @PutMapping("/basvuru-slug")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TenantResponse> basvuruSlug(
            @Valid @RequestBody BasvuruSlugRequest request) {
        return ApiResponse.ok(service.updateBasvuruSlug(request));
    }
}
