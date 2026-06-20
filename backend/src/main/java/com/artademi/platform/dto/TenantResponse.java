package com.artademi.platform.dto;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantStatus;
import java.util.UUID;

/** Tenant yanit DTO'su (kendi tenant'ini gosterir). */
public record TenantResponse(UUID id, String ad, TenantStatus status) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getAd(), t.getStatus());
    }
}
