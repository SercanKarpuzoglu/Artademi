package com.artademi.platform;

import com.artademi.common.ApiResponse;
import com.artademi.platform.dto.CreateTenantRequest;
import com.artademi.platform.dto.CreateTenantResponse;
import com.artademi.platform.dto.PlatformTenantResponse;
import com.artademi.platform.dto.SubscriptionResponse;
import com.artademi.platform.dto.UpdateSubscriptionRequest;
import com.artademi.platform.dto.UpdateTenantStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform (SUPER_ADMIN) tenant yonetimi ucu. {@code /api/platform/**} {@link
 * com.artademi.common.tenant.RequireTenantInterceptor}'tan MUAFTIR (tenant-bagimsiz); bu yuzden
 * tenant_id'siz SUPER_ADMIN erisebilir. TUM uclar yalnizca SUPER_ADMIN (diger roller 403).
 *
 * <p>Kapsam: tenant listele/olustur/durum degistir. admin-user provisioning ve ASKIDA login-engeli
 * AYRI islerdir (bu pakette YOK).
 */
@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformController {

    private final PlatformService service;
    private final SubscriptionService subscriptionService;

    public PlatformController(PlatformService service, SubscriptionService subscriptionService) {
        this.service = service;
        this.subscriptionService = subscriptionService;
    }

    /** Tum tenant'lar; ?status=AKTIF|ASKIDA & ?q= (ad arama) opsiyonel. */
    @GetMapping
    public ApiResponse<List<PlatformTenantResponse>> list(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.list(status, q));
    }

    /**
     * Yeni tenant olustur + ilk ADMIN'i provision et (201). Keycloak admin yaratilamasa bile tenant
     * olusur; yanitta {@code admin.provisioned=false} + {@code warning} doner (yine 201).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tenant durumu (AKTIF/ASKIDA). Idempotent; bilinmeyen id -> 404. */
    @PatchMapping("/{id}/status")
    public ApiResponse<PlatformTenantResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request.status()));
    }

    /**
     * Tenant'i SOFT-DELETE eder ({@code status=SILINDI}): listede gizlenir, kullanicilari kilitlenir.
     * VERI SILINMEZ (status'u AKTIF'e cevirerek geri alinabilir). Idempotent; bilinmeyen id -> 404.
     * Gercek kalici silme (DB + Keycloak temizligi) ayri/elle islemdir.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<PlatformTenantResponse> softDelete(@PathVariable UUID id) {
        return ApiResponse.ok(service.softDelete(id));
    }

    /** Tenant'in abonelik detayi. Bilinmeyen tenant/abonelik -> 404. */
    @GetMapping("/{id}/subscription")
    public ApiResponse<SubscriptionResponse> getSubscription(@PathVariable UUID id) {
        return ApiResponse.ok(subscriptionService.getByTenant(id));
    }

    /**
     * Abonelik odeme/donem guncelle (manuel; iyzico gelene kadar). {@code ODENDI} -> telafi (abonelik
     * AKTIF + tenant ASKIDA ise AKTIF). Bilinmeyen tenant -> 404.
     */
    @PatchMapping("/{id}/subscription")
    public ApiResponse<SubscriptionResponse> updateSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return ApiResponse.ok(
                subscriptionService.applyPayment(id, request.paymentStatus(), request.currentPeriodEnd()));
    }
}
