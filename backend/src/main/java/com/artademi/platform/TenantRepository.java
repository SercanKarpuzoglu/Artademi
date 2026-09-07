package com.artademi.platform;

import java.util.Optional;
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

    /**
     * Public on kayit baglantisini tenant'a cozer.
     *
     * <p>⚠️ Kimliksiz istekten cagrilir. {@code tenant} PLATFORM tablosudur ve tenant
     * filtresinden muaftir, bu yuzden bu sorgu TenantContext bos iken de calisir —
     * zaten amac tenant'i BELIRLEMEKTIR.
     */
    Optional<Tenant> findByBasvuruSlug(String basvuruSlug);

    /** Mukerrer ad engellemesi (buyuk/kucuk harf duyarsiz). */
    boolean existsByAdIgnoreCase(String ad);
}
