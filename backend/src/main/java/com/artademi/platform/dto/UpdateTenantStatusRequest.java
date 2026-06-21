package com.artademi.platform.dto;

import com.artademi.platform.TenantStatus;
import jakarta.validation.constraints.NotNull;

/** Tenant durumu degisikligi (AKTIF/ASKIDA). Gecersiz deger -> 400 (enum bind hatasi). */
public record UpdateTenantStatusRequest(
        @NotNull(message = "Durum zorunludur")
        TenantStatus status) {
}
