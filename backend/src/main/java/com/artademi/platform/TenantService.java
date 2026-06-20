package com.artademi.platform;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.TenantRequiredException;
import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.dto.TenantResponse;
import com.artademi.platform.dto.UpdateTenantRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant is kurallari. İzolasyon: tenant HER ZAMAN aktif {@link TenantContext}'ten okunur; id
 * disaridan ALINMAZ, dolayisiyla bir kullanici baska tenant'i goremez/degistiremez.
 *
 * <p>Tenant tablosu tenant filtresine tabi olmadigindan {@code findById} dogrudan kullanilabilir.
 */
@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    /** Oturum sahibinin kendi tenant'i. */
    @Transactional(readOnly = true)
    public TenantResponse current() {
        return TenantResponse.from(loadCurrent());
    }

    /** Oturum sahibinin tenant'inin adi (yoksa null) — /api/me icin hafif yardimci. */
    @Transactional(readOnly = true)
    public String currentName() {
        UUID id = TenantContext.get();
        if (id == null) {
            return null;
        }
        return repository.findById(id).map(Tenant::getAd).orElse(null);
    }

    /** Kendi tenant'inin adini gunceller (yalnizca ADMIN — controller'da zorlanir). */
    @Transactional
    public TenantResponse updateName(UpdateTenantRequest req) {
        Tenant tenant = loadCurrent();
        tenant.setAd(req.ad().trim());
        return TenantResponse.from(tenant);
    }

    private Tenant loadCurrent() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new TenantRequiredException("Tenant bağlamı yok");
        }
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant bulunamadı: " + id));
    }
}
