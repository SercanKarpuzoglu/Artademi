package com.artademi.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Subscription repository. Subscription tablosu tenant filtresine TABI DEGILDIR (platform-duzeyi);
 * bu yuzden {@code findByTenantId}/{@code findAll} guvenlidir — {@code findScopedById} kuralinin
 * istisnasi (yalnizca SUPER_ADMIN/platform mantigi ve gunluk degerlendirme job'i kullanir).
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** Bir tenant'in aboneligi (1-1). */
    Optional<Subscription> findByTenantId(UUID tenantId);

    /** Birden cok tenant'in aboneligi (liste ozeti icin toplu yukleme — N+1 onler). */
    List<Subscription> findByTenantIdIn(List<UUID> tenantIds);
}
