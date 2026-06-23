package com.artademi.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Platform konsolundan bir tenant'a kullanici ekleme istegi (SUPER_ADMIN). {@code tenant_id} YOL'dan
 * (path) alinir, body'den DEGIL. Roller atanabilir realm rolleriyle sinirli (SUPER_ADMIN asla).
 */
public record CreateTenantUserRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur")
        String kullaniciAdi,

        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotBlank(message = "Soyad zorunludur")
        String soyad,

        @Email(message = "Geçerli bir e-posta giriniz")
        String email,

        String telefon,

        @NotEmpty(message = "En az bir rol seçilmeli")
        List<String> roller) {
}
