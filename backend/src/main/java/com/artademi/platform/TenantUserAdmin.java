package com.artademi.platform;

import com.artademi.platform.dto.CreateTenantUserRequest;
import com.artademi.platform.dto.TenantUserView;
import java.util.List;
import java.util.UUID;

/**
 * Platform (SUPER_ADMIN) konsolundan BELIRLI bir tenant'in kullanicilarini yoneten port.
 *
 * <p>Arayuz {@code platform} paketinde, somut uygulama {@code user} paketinde
 * ({@code KeycloakTenantUserAdmin}) — {@link TenantAdminProvisioner} ile ayni desen (paket dongusu
 * yok, platform testlerinde {@code @MockBean}). Tenant {@code tenantId} ile DISARIDAN verilir
 * (acting kullanici SUPER_ADMIN, tenant'siz); normal {@code /api/users} ise acting admin'in
 * TenantContext'ine baglidir — bu yuzden ayri bir yol gerekti.
 *
 * <p>Izolasyon: tum islemler {@code tenantId}'ye kapanir — listede yalnizca o tenant'in
 * kullanicilari; silmede hedefin {@code tenant_id} attribute'u {@code tenantId} ile eslesmezse 404.
 */
public interface TenantUserAdmin {

    /** Verilen tenant'in kullanicilari (Keycloak {@code tenant_id} attribute filtresi). */
    List<TenantUserView> list(UUID tenantId);

    /** Verilen tenant'a kullanici ekler; {@code tenant_id} = {@code tenantId} (body'den DEGIL). */
    TenantUserView create(UUID tenantId, CreateTenantUserRequest req);

    /** Kullaniciyi siler; hedefin tenant'i {@code tenantId} degilse 404 (sizinti yok). */
    void delete(UUID tenantId, String userId);
}
