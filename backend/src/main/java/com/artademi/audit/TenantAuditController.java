package com.artademi.audit;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurum ici islem kaydi — {@code GET /api/audit} (SADECE ADMIN, kendi kurumu).
 *
 * <p>Tenant filtresi otomatik: baska kurumun kaydi teknik olarak DONEMEZ.
 * Yalnizca OKUMA — kayit yazma interceptor'dan gecer, disaridan eklenemez/silinemez.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class TenantAuditController {

    private static final int MAX_SIZE = 100;

    private final TenantAuditRepository repository;

    public TenantAuditController(TenantAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<AuditSatiri>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<TenantAudit> result = repository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE)));
        return ApiResponse.ok(result.map(AuditSatiri::from).getContent(), PageMeta.of(result));
    }

    /** Konsol satiri. */
    public record AuditSatiri(UUID id, String actor, String actorAd, String eylem, String metot,
            String yol, String kayitId, Instant createdAt) {

        static AuditSatiri from(TenantAudit a) {
            return new AuditSatiri(a.getId(), a.getActor(), a.getActorAd(), a.getEylem(),
                    a.getMetot(), a.getYol(), a.getKayitId(), a.getCreatedAt());
        }
    }
}
