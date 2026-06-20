package com.artademi.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Oturum sahibinin kendi profilini guncelleme istegi (/api/me PUT). Rol/tenant_id/
 * must_change_password DEGISTIRILEMEZ.
 */
public record UpdateMeRequest(
        @NotBlank String ad,
        @NotBlank String soyad,
        String telefon,
        @Email String email) {
}
