package com.artademi.platform;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant repository. Tenant tablosu tenant filtresine TABI OLMADIGINDAN standart {@code findById}
 * guvenle kullanilabilir; izolasyon servis katmaninda saglanir (yalnizca aktif {@code TenantContext}
 * id'si okunur, disaridan id alinmaz).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
