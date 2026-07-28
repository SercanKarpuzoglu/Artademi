package com.artademi.billing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BillingEvent repository (platform-duzeyi; tenant filtresine tabi degil).
 */
public interface BillingEventRepository extends JpaRepository<BillingEvent, UUID> {

    /** Idempotency: bu bildirim daha once islendi mi? */
    boolean existsByProviderAndDedupKey(String provider, String dedupKey);
}
