package com.artademi.platform.dto;

import jakarta.validation.constraints.NotBlank;

/** Yeni tenant olusturma istegi (SUPER_ADMIN). id/status/createdAt sunucuda atanir. */
public record CreateTenantRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad) {
}
