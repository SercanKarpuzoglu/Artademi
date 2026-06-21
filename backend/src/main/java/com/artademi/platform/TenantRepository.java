package com.artademi.platform;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Tenant repository. Tenant tablosu tenant filtresine TABI OLMADIGINDAN standart {@code findById}/
 * {@code findAll} guvenle kullanilabilir — bu, {@code findScopedById} kuralinin TEK ISTISNASIDIR
 * (platform-duzeyi, tenant-bagimsiz). Normal is entity'lerinde ASLA boyle yapilmaz.
 *
 * <p>Tenant izolasyonu burada GEREKMEZ: yalnizca SUPER_ADMIN bu repository'yi platform uclarindan
 * kullanir (tum tenant'lari yonetmek icin); tenant kullanicilari kendi tenant'ini {@code TenantService}
 * uzerinden okur.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    /** Mukerrer ad engellemesi (buyuk/kucuk harf duyarsiz). */
    boolean existsByAdIgnoreCase(String ad);
}
