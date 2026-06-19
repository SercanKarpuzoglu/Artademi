package com.artademi.payout;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.payout.dto.CalculatePayoutRequest;
import com.artademi.payout.dto.PayoutResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hakedis (payout) ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki (MAAS HASSAS): tum uclar (hesapla, onizle, get, list, ode) yalnizca ADMIN. Hakediş maas
 * bilgisidir; FRONTDESK_ACCOUNTING dahil diger roller GORMEZ (403).
 *
 * <p>DELETE YOK.
 */
@RestController
@RequestMapping("/api/payouts")
@PreAuthorize("hasRole('ADMIN')")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    /** Hakedisi hesaplar ve kaydeder, 201. Mukerrer (ogretmen+donem) -> 409. */
    @PostMapping("/hesapla")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PayoutResponse> hesapla(@Valid @RequestBody CalculatePayoutRequest request) {
        return ApiResponse.ok(service.hesapla(request));
    }

    /** Kayitsiz onizleme: ayni dokumu uretir ama satir OLUSTURMAZ. */
    @GetMapping("/onizle")
    public ApiResponse<PayoutResponse> onizle(
            @RequestParam Long ogretmenId,
            @RequestParam String donem,
            @RequestParam(required = false) BigDecimal kdvOrani) {
        return ApiResponse.ok(service.onizle(ogretmenId, donem, kdvOrani));
    }

    /** Tek hakediş (yoksa 404). */
    @GetMapping("/{id}")
    public ApiResponse<PayoutResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Filtreli/sayfali liste: ?ogretmenId=&donem=&durum=&page=0&size=20 (hepsi opsiyonel). */
    @GetMapping
    public ApiResponse<List<PayoutResponse>> list(
            @RequestParam(required = false) Long ogretmenId,
            @RequestParam(required = false) String donem,
            @RequestParam(required = false) PayoutDurumu durum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("donem").descending());
        Page<PayoutResponse> result = service.search(ogretmenId, donem, durum, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }

    /** Hakedisi ODENDI olarak isaretler (odemeTarihi = bugun). */
    @PatchMapping("/{id}/ode")
    public ApiResponse<PayoutResponse> ode(@PathVariable Long id) {
        return ApiResponse.ok(service.ode(id));
    }
}
