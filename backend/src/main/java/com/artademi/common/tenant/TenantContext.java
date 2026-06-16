package com.artademi.common.tenant;

/**
 * Istek basina aktif tenant kimligini tutan baglam (ThreadLocal).
 *
 * <p>Tenant kaynagini okuyan TEK yer {@code TenantFilter}'dir; su an gecici olarak
 * {@code X-Tenant-Id} header'indan gelir. 2b-3'te kaynak Keycloak JWT'sindeki
 * {@code tenant_id} claim'iyle degisecek — bu sinif degismeden kalir.
 *
 * <p>{@link #get()} henuz set edilmemisse {@code null} dondurebilir (or. /api/ping,
 * /actuator veya header'siz istekler).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
