package com.artademi.platform;

/**
 * Abonelik durumu akisi: {@link #DENEME} → {@link #AKTIF} → {@link #ODEME_BEKLIYOR} (grace, TAM
 * erisim surer) → {@link #ASKIDA} (erisim kesilir). {@link #IPTAL} manuel sonlandirma.
 *
 * <p><b>Net ayrim:</b> {@code ODEME_BEKLIYOR} = uyari (tenant.status AKTIF kalir); {@code ASKIDA} =
 * kesinti (tenant.status da ASKIDA olur, TenantStatusInterceptor is uclarini 403 ile keser).
 */
public enum SubscriptionStatus {
    DENEME,
    AKTIF,
    ODEME_BEKLIYOR,
    ASKIDA,
    IPTAL
}
