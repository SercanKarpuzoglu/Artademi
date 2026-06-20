package com.artademi.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Yeni kullanici olusturma istegi (/api/users POST). tenant_id ASLA istemciden alinmaz;
 * acting admin'in TenantContext'inden set edilir. {@code roller} servis katmaninda
 * MANAGEABLE_ROLES'e gore dogrulanir (SUPER_ADMIN atanamaz).
 */
public record CreateUserRequest(
        @NotBlank String kullaniciAdi,
        @NotBlank String ad,
        @NotBlank String soyad,
        @Email String email,
        String telefon,
        @NotEmpty List<String> roller) {
}
