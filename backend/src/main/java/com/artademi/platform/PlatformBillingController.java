package com.artademi.platform;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.platform.PlatformBillingService.Filtre;
import com.artademi.platform.dto.BillingEventRow;
import com.artademi.platform.dto.PlatformSubscriptionRow;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform odeme/abonelik takibi uclari (SADECE SUPER_ADMIN).
 *
 * <ul>
 *   <li>{@code GET /api/platform/billing/subscriptions?filtre=&q=} — kurum bazli odeme durumu</li>
 *   <li>{@code GET /api/platform/billing/events?page=&size=} — webhook/mutabakat izleri</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/platform/billing")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformBillingController {

    /** Sayfa boyutu tavani: konsol tablosu icin makul ust sinir. */
    private static final int MAX_SIZE = 100;

    private final PlatformBillingService service;

    public PlatformBillingController(PlatformBillingService service) {
        this.service = service;
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<PlatformSubscriptionRow>> subscriptions(
            @RequestParam(required = false) Filtre filtre,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(service.subscriptionRows(filtre, q));
    }

    @GetMapping("/events")
    public ApiResponse<List<BillingEventRow>> events(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BillingEventRow> result = service.events(PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
