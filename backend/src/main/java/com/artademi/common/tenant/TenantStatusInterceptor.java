package com.artademi.common.tenant;

import com.artademi.common.exception.TenantSuspendedException;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Katman 3 (tenant durumu): aktif tenant {@code ASKIDA} ise is ucuna erisimi 403 ile reddeder.
 * {@link RequireTenantInterceptor}'tan SONRA calisir (once tenant var mi → sonra askida mi);
 * sira {@link TenantWebConfig}'te kayit sirasiyla garanti edilir.
 *
 * <p>Tenant tablosu tenant filtresine TABI DEGILDIR; bu yuzden {@code findById} burada DOGRUDUR
 * (platform-duzeyi sorgu, findScopedById kuralinin istisnasi — bkz. TenantRepository).
 */
@Component
public class TenantStatusInterceptor implements HandlerInterceptor {

    private final TenantRepository tenantRepository;

    public TenantStatusInterceptor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        UUID tenantId = TenantContext.get();
        // 1) Tenant baglami yoksa (SUPER_ADMIN / null) status kontrolu anlamsiz -> atla.
        //    (RequireTenantInterceptor zaten tenant gerektiren uclarda 400 atar.)
        if (tenantId == null) {
            return true;
        }
        // 2-3) Tenant kaydi yoksa: guvenli taraf, devam (eksik kayit ayri bir veri sorunudur;
        //      burada 403 atmak yaniltici olur).
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return true;
        }
        // 4) ASKIDA veya SILINDI (soft-delete) -> 403 TENANT_SUSPENDED. Ikisi de is uclarini keser;
        //    /api/me muaf (bkz. TenantWebConfig) — kullanici durumunu gorebilir.
        if (tenant.getStatus() == TenantStatus.ASKIDA || tenant.getStatus() == TenantStatus.SILINDI) {
            throw new TenantSuspendedException(
                    "Kurumunuzun erişimi askıya alınmıştır. Lütfen yöneticinizle iletişime geçin.");
        }
        // 5) AKTIF -> devam.
        return true;
    }
}
