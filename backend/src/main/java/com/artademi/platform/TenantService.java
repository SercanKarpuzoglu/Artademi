package com.artademi.platform;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.TenantRequiredException;
import com.artademi.common.tenant.TenantContext;
import com.artademi.common.exception.ConflictException;
import com.artademi.platform.dto.BasvuruSlugRequest;
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

    /**
     * Kurumun public on kayit baglanti adini belirler; bos gonderim ozelligi KAPATIR.
     *
     * <p>Slug tum kurumlar arasinda benzersizdir (V24'te kismi unique indeks). Baskasinin
     * kullandigi bir ad istenirse 409 doner — veritabani hatasini kullaniciya ham
     * gostermek yerine anlasilir mesaj veririz.
     */
    @Transactional
    public TenantResponse updateBasvuruSlug(BasvuruSlugRequest req) {
        Tenant tenant = loadCurrent();
        if (req.kapatiliyorMu()) {
            tenant.setBasvuruSlug(null);
            return TenantResponse.from(tenant);
        }
        String slug = req.slug().trim();
        repository.findByBasvuruSlug(slug)
                .filter(sahip -> !sahip.getId().equals(tenant.getId()))
                .ifPresent(sahip -> {
                    throw new ConflictException(
                            "Bu bağlantı adı başka bir kurum tarafından kullanılıyor.");
                });
        tenant.setBasvuruSlug(slug);
        return TenantResponse.from(tenant);
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
