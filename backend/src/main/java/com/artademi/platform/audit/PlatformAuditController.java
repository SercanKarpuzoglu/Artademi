package com.artademi.platform.audit;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Denetim izi listesi — {@code GET /api/platform/audit?page=&size=} (SADECE SUPER_ADMIN).
 * Yalnizca OKUMA: iz yazma, ilgili is islemlerinin icinden yapilir; disaridan kayit eklenemez.
 */
@RestController
@RequestMapping("/api/platform/audit")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformAuditController {

    private static final int MAX_SIZE = 100;

    private final AuditService service;

    public PlatformAuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AuditRow>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<PlatformAudit> result = service.listele(PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE)));
        return ApiResponse.ok(result.map(AuditRow::from).getContent(), PageMeta.of(result));
    }

    /** Konsol satiri — {@code actionEtiketi} Turkce gosterim icin hazir gelir. */
    public record AuditRow(
            java.util.UUID id,
            String actor,
            AuditAction action,
            String actionEtiketi,
            java.util.UUID targetTenantId,
            String targetAd,
            String detail,
            java.time.Instant createdAt) {

        static AuditRow from(PlatformAudit a) {
            return new AuditRow(a.getId(), a.getActor(), a.getAction(), a.getAction().etiket(),
                    a.getTargetTenantId(), a.getTargetAd(), a.getDetail(), a.getCreatedAt());
        }
    }
}
