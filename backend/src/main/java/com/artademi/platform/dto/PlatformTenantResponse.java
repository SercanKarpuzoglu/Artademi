package com.artademi.platform.dto;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/** Platform (SUPER_ADMIN) tenant yanit DTO'su — createdAt dahil. */
public record PlatformTenantResponse(UUID id, String ad, TenantStatus status, Instant createdAt) {

    public static PlatformTenantResponse from(Tenant t) {
        return new PlatformTenantResponse(t.getId(), t.getAd(), t.getStatus(), t.getCreatedAt());
    }
}
