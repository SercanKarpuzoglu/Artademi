package com.artademi.platform;

import com.artademi.common.ApiResponse;
import com.artademi.platform.dto.CreateTenantUserRequest;
import com.artademi.platform.dto.TenantUserView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform (SUPER_ADMIN) — BELIRLI bir tenant'in kullanicilarini yonetir. {@code /api/platform/**}
 * RequireTenant'tan muaftir (SUPER_ADMIN tenant'sizdir); tenant PATH'ten ({@code tenantId}) gelir.
 * TUM uclar yalniz SUPER_ADMIN. Izolasyon {@link TenantUserAdmin}'de fail-closed (tenant_id eslemesi).
 */
@RestController
@RequestMapping("/api/platform/tenants/{tenantId}/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformUserController {

    private final TenantUserAdmin tenantUserAdmin;

    public PlatformUserController(TenantUserAdmin tenantUserAdmin) {
        this.tenantUserAdmin = tenantUserAdmin;
    }

    /** Tenant'in kullanicilari (ad/soyad/email/rol/aktiflik). */
    @GetMapping
    public ApiResponse<List<TenantUserView>> list(@PathVariable UUID tenantId) {
        return ApiResponse.ok(tenantUserAdmin.list(tenantId));
    }

    /** Tenant'a yeni kullanici ekle (201). tenant_id PATH'ten atanir; ilk parola Artademi2026!. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TenantUserView> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateTenantUserRequest request) {
        return ApiResponse.ok(tenantUserAdmin.create(tenantId, request));
    }

    /** Kullaniciyi sil; hedef bu tenant'a ait degilse 404 (sizinti yok). */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> delete(@PathVariable UUID tenantId, @PathVariable String userId) {
        tenantUserAdmin.delete(tenantId, userId);
        return ApiResponse.ok(null);
    }
}
