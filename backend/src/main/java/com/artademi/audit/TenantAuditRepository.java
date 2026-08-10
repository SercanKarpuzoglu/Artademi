package com.artademi.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Kurum ici islem kaydi repository. Tenant filtresi OTOMATIK uygulanir (TenantAware) —
 * bu yuzden burada tenant_id kosulu yazmaya gerek yoktur ve baska kurumun kaydi ASLA gelmez.
 */
public interface TenantAuditRepository extends JpaRepository<TenantAudit, UUID> {

    /** Konsol listesi: en yeni once (tenant filtresi otomatik). */
    Page<TenantAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
