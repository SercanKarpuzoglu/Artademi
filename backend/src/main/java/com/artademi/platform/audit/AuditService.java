package com.artademi.platform.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Denetim izi yazma/okuma.
 *
 * <p>⚠️ Yazma, tetikleyen islemin AYNI TRANSACTION'ında yapilir: iz yazilamiyorsa islem de geri
 * alinir. "Sessizce izsiz kalan islem" olmasi, islemin basarisiz olmasindan daha kotudur.
 *
 * <p>Actor JWT'den okunur ({@code preferred_username}); baglam yoksa "sistem" yazilir (scheduler
 * gibi kullanicisiz tetiklemeler).
 */
@Service
public class AuditService {

    private static final String SISTEM = "sistem";

    private final PlatformAuditRepository repository;

    public AuditService(PlatformAuditRepository repository) {
        this.repository = repository;
    }

    /** Denetim kaydi yazar (cagiran transaction'a katilir). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void kaydet(AuditAction action, UUID targetTenantId, String targetAd, String detail) {
        repository.save(PlatformAudit.of(actor(), action, targetTenantId, targetAd, detail));
    }

    /** Transaction'i olmayan cagrilar icin (yeni transaction acar). */
    @Transactional
    public void kaydetBagimsiz(AuditAction action, UUID targetTenantId, String targetAd,
            String detail) {
        repository.save(PlatformAudit.of(actor(), action, targetTenantId, targetAd, detail));
    }

    @Transactional(readOnly = true)
    public Page<PlatformAudit> listele(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** Oturumdaki platform yoneticisinin kullanici adi; baglam yoksa "sistem". */
    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return SISTEM;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
            String name = jwt.getClaimAsString("name");
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        String adi = auth.getName();
        return adi == null || adi.isBlank() ? SISTEM : adi;
    }
}
