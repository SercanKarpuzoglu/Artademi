package com.artademi.common.tenant;

import java.util.UUID;

/**
 * Istek basina aktif tenant kimligini tutan baglam (ThreadLocal).
 *
 * <p>Tenant kaynagini okuyan TEK yer {@code TenantFilter}'dir; su an gecici olarak
 * {@code X-Tenant-Id} header'indan gelir. 2b-3'te kaynak Keycloak JWT'sindeki
 * {@code tenant_id} claim'iyle degisecek — bu sinif degismeden kalir.
 *
 * <p>{@link #get()} henuz set edilmemisse {@code null} dondurebilir (or. /api/ping,
 * /actuator veya header'siz/gecersiz istekler).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
