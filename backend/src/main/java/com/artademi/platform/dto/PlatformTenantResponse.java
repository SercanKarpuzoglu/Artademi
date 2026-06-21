package com.artademi.platform.dto;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Platform (SUPER_ADMIN) tenant yanit DTO'su — createdAt + abonelik ozeti dahil. {@code subscription}
 * liste ucunda doldurulur; tekil olusturma/durum-degisikligi yanitlarinda null olabilir.
 */
public record PlatformTenantResponse(
        UUID id,
        String ad,
        TenantStatus status,
        Instant createdAt,
        SubscriptionResponse subscription) {

    /** Abonelik ozeti olmadan (olusturma/durum yanitlari). */
    public static PlatformTenantResponse from(Tenant t) {
        return from(t, null);
    }

    /** Abonelik ozetiyle (liste ucu). */
    public static PlatformTenantResponse from(Tenant t, SubscriptionResponse subscription) {
        return new PlatformTenantResponse(
                t.getId(), t.getAd(), t.getStatus(), t.getCreatedAt(), subscription);
    }
}
