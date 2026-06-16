package com.artademi.common.tenant;

import java.util.function.Supplier;

/**
 * {@code tenantFilter}'in parametre cozucusu. Hibernate, filtre her oturumda
 * otomatik etkin oldugundan ({@code autoEnabled = true}) bu cozuyucuyu cagirarak
 * aktif tenant'i belirler.
 *
 * <p><b>Fail-closed:</b> {@link TenantContext} bossa hicbir kayitla eslesmeyen
 * {@link #NO_TENANT} (-1) dondurulur. Boylece tenant baglami yoksa TenantAware
 * tablolar BOS sonuc verir; filtre HICBIR ZAMAN kapali kalmaz.
 */
public class TenantIdResolver implements Supplier<Long> {

    /** Hicbir tenant_id ile eslesmeyen sentinel. */
    public static final Long NO_TENANT = -1L;

    @Override
    public Long get() {
        Long tenantId = TenantContext.get();
        return tenantId != null ? tenantId : NO_TENANT;
    }
}
