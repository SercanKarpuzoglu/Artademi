package com.artademi.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Yeni tenant olusturma istegi (SUPER_ADMIN). Tenant ile birlikte ilk ADMIN kullanicisi da
 * provision edilir. id/status/createdAt sunucuda atanir; yeni admin'in tenant_id'si yeni tenant'in
 * id'sidir (istemciden ALINMAZ).
 */
public record CreateTenantRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotBlank(message = "Yönetici e-postası zorunludur")
        @Email(message = "Geçerli bir e-posta giriniz")
        String adminEmail,

        @NotBlank(message = "Yönetici adı zorunludur")
        String adminAd,

        @NotBlank(message = "Yönetici soyadı zorunludur")
        String adminSoyad) {
}
