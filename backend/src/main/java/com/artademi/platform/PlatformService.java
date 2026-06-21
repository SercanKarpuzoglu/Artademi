package com.artademi.platform;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.platform.dto.CreateTenantRequest;
import com.artademi.platform.dto.PlatformTenantResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform (SUPER_ADMIN) tenant yonetimi is kurallari. Tenant tablosu tenant filtresine TABI
 * DEGILDIR; bu yuzden burada tum tenant'lar gorulebilir/yonetilebilir (platform-duzeyi). Yetki
 * (SUPER_ADMIN) controller'da {@code @PreAuthorize} ile zorlanir.
 *
 * <p>Bu pakette SADECE Tenant CRUD vardir: admin-user provisioning ve ASKIDA login-engeli AYRI
 * isler (bkz. handoff).
 */
@Service
public class PlatformService {

    private final TenantRepository repository;

    public PlatformService(TenantRepository repository) {
        this.repository = repository;
    }

    /** Tum tenant'lar; status ve q (ad contains, ignore-case) opsiyonel filtreler. */
    @Transactional(readOnly = true)
    public List<PlatformTenantResponse> list(TenantStatus status, String q) {
        Specification<Tenant> spec = Specification
                .where(hasStatus(status))
                .and(adContains(q));
        return repository.findAll(spec, org.springframework.data.domain.Sort.by("ad").ascending())
                .stream()
                .map(PlatformTenantResponse::from)
                .toList();
    }

    /** Yeni tenant olusturur (status AKTIF). Mukerrer ad (ignore-case) -> 409. */
    @Transactional
    public PlatformTenantResponse create(CreateTenantRequest req) {
        String ad = req.ad().trim();
        if (repository.existsByAdIgnoreCase(ad)) {
            throw new ConflictException("Bu ada sahip bir kurum zaten var: " + ad);
        }
        return PlatformTenantResponse.from(repository.save(Tenant.create(ad)));
    }

    /** Tenant durumunu degistirir (idempotent: ayni status -> no-op, 200). Bilinmeyen id -> 404. */
    @Transactional
    public PlatformTenantResponse changeStatus(UUID id, TenantStatus status) {
        Tenant tenant = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant bulunamadı: " + id));
        if (tenant.getStatus() != status) {
            tenant.setStatus(status);
        }
        return PlatformTenantResponse.from(tenant);
    }

    private static Specification<Tenant> hasStatus(TenantStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<Tenant> adContains(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("ad")), "%" + q.trim().toLowerCase() + "%");
        };
    }
}
