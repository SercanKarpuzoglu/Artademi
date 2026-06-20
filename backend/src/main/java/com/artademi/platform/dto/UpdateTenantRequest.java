package com.artademi.platform.dto;

import jakarta.validation.constraints.NotBlank;

/** Tenant adi guncelleme istegi (SADECE ADMIN, kendi tenant'i). status bu uctan degismez. */
public record UpdateTenantRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad) {
}
