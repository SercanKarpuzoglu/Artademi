package com.artademi.platform;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.platform.dto.CreateTenantRequest;
import com.artademi.platform.dto.CreateTenantResponse;
import com.artademi.platform.dto.PlatformTenantResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform (SUPER_ADMIN) tenant yonetimi is kurallari. Tenant tablosu tenant filtresine TABI
 * DEGILDIR; bu yuzden burada tum tenant'lar gorulebilir/yonetilebilir (platform-duzeyi). Yetki
 * (SUPER_ADMIN) controller'da {@code @PreAuthorize} ile zorlanir.
 *
 * <p>Tenant olusturma ARTIK ilk ADMIN kullanicisini da provision eder ({@link TenantAdminProvisioner}
 * — uygulamasi {@code user} paketinde). ASKIDA login-engeli AYRI istir (bkz. handoff).
 */
@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    private final TenantRepository repository;
    private final TenantAdminProvisioner adminProvisioner;
    /** Kendi proxy'si: {@code saveTenant}'i AYRI bir transaction'da cagirip ONCE commit etmek icin. */
    private final PlatformService self;

    public PlatformService(TenantRepository repository, TenantAdminProvisioner adminProvisioner,
            @Lazy PlatformService self) {
        this.repository = repository;
        this.adminProvisioner = adminProvisioner;
        this.self = self;
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

    /**
     * Yeni tenant olusturur ve ilk ADMIN kullanicisini provision eder. <b>Sira (a):</b> once tenant
     * AYRI bir transaction'da DB'ye yazilip COMMIT edilir ({@link #saveTenant}), SONRA Keycloak admin
     * yaratilir. Keycloak patlarsa tenant GERI ALINMAZ ("silme yok" ilkesi); {@code provisioned=false}
     * + warning doner (HTTP yine 201 — tenant gercekten olustu).
     *
     * <p>Bu metot transactional DEGILDIR; tenant commit'i {@code self.saveTenant} icinde gerceklesir
     * (proxy uzerinden ayri tx). Mukerrer ad (ignore-case) -> 409 (Keycloak'a hic gidilmez).
     */
    public CreateTenantResponse create(CreateTenantRequest req) {
        PlatformTenantResponse tenant = self.saveTenant(req); // ayri tx: tenant ONCE commit edilir
        String email = req.adminEmail().trim();
        try {
            TenantAdminProvisioner.ProvisionedAdmin admin = adminProvisioner.provision(
                    tenant.id(), email, req.adminAd().trim(), req.adminSoyad().trim());
            return CreateTenantResponse.provisioned(tenant, admin.username(), admin.email());
        } catch (RuntimeException e) {
            // Tenant zaten commit'li; SILME YOK. Uyari ile don.
            log.warn("Tenant {} olustu ancak admin provisioning basarisiz: {}",
                    tenant.id(), e.getMessage());
            return CreateTenantResponse.failed(tenant, email,
                    "Tenant oluşturuldu ancak admin kullanıcı yaratılamadı: " + e.getMessage()
                            + ". Lütfen /kullanicilar'dan elle ekleyin.");
        }
    }

    /**
     * Tenant'i kendi transaction'inda kaydeder (status AKTIF) ve commit eder. Mukerrer ad
     * (ignore-case) -> 409. {@link #create} bunu proxy ({@code self}) uzerinden cagirir ki provisioning
     * oncesi tenant gercekten commit edilmis olsun.
     */
    @Transactional
    public PlatformTenantResponse saveTenant(CreateTenantRequest req) {
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
