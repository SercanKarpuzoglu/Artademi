package com.artademi.billing;

import com.artademi.billing.dto.BillingSubscriptionResponse;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.billing.dto.CheckoutStartRequest;
import com.artademi.common.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Odeme/abonelik uclari.
 *
 * <ul>
 *   <li>{@code GET /api/billing/subscription} + {@code POST /api/billing/checkout} — SADECE ADMIN,
 *       kendi tenant'i (TenantContext). ⚠️ {@code /api/billing/**} TenantStatusInterceptor'dan
 *       MUAFTIR: ASKIDA tenant'in admin'i odeme yapip erisimini GERI ACABILMELIDIR
 *       (subscription-billing skill kurali).</li>
 *   <li>{@code POST /api/billing/callback} — iyzico'nun checkout sonrasi browser POST'u; JWT YOK
 *       (permitAll). Token tek kullanimlik ve sonuc SAGLAYICIDAN dogrulanir; yanit web'e 302.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService service;
    private final BillingProperties props;

    public BillingController(BillingService service, BillingProperties props) {
        this.service = service;
        this.props = props;
    }

    /** Kendi tenant'inin abonelik ozeti (odeme sayfasi icin). */
    @GetMapping("/subscription")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BillingSubscriptionResponse> subscription() {
        return ApiResponse.ok(service.ownSubscription());
    }

    /** iyzico hosted checkout baslatir; donen formContent web sayfasina gomulur. */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CheckoutSession> checkout(@Valid @RequestBody CheckoutStartRequest request) {
        return ApiResponse.ok(service.startCheckout(request.toCustomer()));
    }

    /**
     * iyzico callback'i (browser form POST, JWT'siz). Sonuc saglayicidan dogrulanir, kullanici
     * web'deki abonelik sayfasina yonlendirilir. Bilinmeyen/suresi gecmis token → hata sayfasi
     * (404 sizdirmayiz; kullaniciya redirect daha dogru UX).
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("token") String token) {
        boolean success;
        try {
            success = service.completeCheckout(token);
        } catch (RuntimeException e) {
            success = false;
        }
        URI target = URI.create(props.webReturnUrl() + "?sonuc=" + (success ? "basarili" : "hata"));
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
