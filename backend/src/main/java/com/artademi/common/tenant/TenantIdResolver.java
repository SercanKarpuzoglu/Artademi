package com.artademi.common.tenant;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code tenantFilter}'in parametre cozucusu. Hibernate, filtre her oturumda
 * otomatik etkin oldugundan ({@code autoEnabled = true}) bu cozuyucuyu cagirarak
 * aktif tenant'i belirler.
 *
 * <p><b>Fail-closed:</b> {@link TenantContext} bossa hicbir kayitla eslesmeyen
 * {@link #NO_TENANT} (tum-sifir UUID) dondurulur. Boylece tenant baglami yoksa
 * TenantAware tablolar BOS sonuc verir; filtre HICBIR ZAMAN kapali kalmaz.
 */
public class TenantIdResolver implements Supplier<UUID> {

    /** Hicbir tenant_id ile eslesmeyen sentinel: 00000000-0000-0000-0000-000000000000. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID get() {
        UUID tenantId = TenantContext.get();
        return tenantId != null ? tenantId : NO_TENANT;
    }
}
