package com.artademi.platform.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Denetim izi repository (platform-duzeyi; tenant filtresine tabi degil).
 * Yalnizca INSERT + SELECT kullanilir — kayit guncellenmez/silinmez.
 */
public interface PlatformAuditRepository extends JpaRepository<PlatformAudit, UUID> {

    /** Konsol listesi (en yeni once). */
    Page<PlatformAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Bir kuruma ait iz (kurum detay ekrani). */
    List<PlatformAudit> findByTargetTenantIdOrderByCreatedAtDesc(UUID targetTenantId);
}
