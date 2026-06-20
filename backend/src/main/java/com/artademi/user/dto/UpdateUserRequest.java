package com.artademi.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Kullanici guncelleme istegi (/api/users/{id} PUT). kullaniciAdi/tenant_id DEGISMEZ;
 * {@code roller} sadece MANAGEABLE_ROLES kumesi icinde uzlastirilir.
 */
public record UpdateUserRequest(
        @NotBlank String ad,
        @NotBlank String soyad,
        String telefon,
        @Email String email,
        @NotEmpty List<String> roller) {
}
