package com.artademi.finance;

import com.artademi.common.ApiResponse;
import com.artademi.finance.dto.AccrualGenerationResult;
import com.artademi.finance.dto.GenerateAccrualRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Otomatik aylik tahakkuk uretimi ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki: toplu borc uretimi YONETIM isidir -> SADECE ADMIN. Bu yuzden ayri controller'dadir
 * (AccrualController FRONTDESK_ACCOUNTING'e de acik; uretim ona KAPALI). Diger roller -> 403.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AccrualGenerationController {

    private final AccrualGenerationService service;

    public AccrualGenerationController(AccrualGenerationService service) {
        this.service = service;
    }

    /** Donem icin tahakkuklari uretir ve kaydeder (idempotent). */
    @PostMapping("/api/accruals/uret")
    public ApiResponse<AccrualGenerationResult> uret(@Valid @RequestBody GenerateAccrualRequest request) {
        return ApiResponse.ok(service.uret(request.donem()));
    }

    /** Onizleme: ne uretilecegini doner, KAYIT OLUSTURMAZ. */
    @GetMapping("/api/accruals/uret-onizle")
    public ApiResponse<AccrualGenerationResult> onizle(@RequestParam String donem) {
        return ApiResponse.ok(service.onizle(donem));
    }
}
