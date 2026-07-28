package com.artademi.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Landing iletisim formu istegi (artademi.com — JWT'siz, herkese acik uc).
 *
 * <p>{@code website} bir HONEYPOT alanidir: formda gizlidir (display:none), gercek kullanici
 * doldurmaz; botlar doldurur. Dolu gelirse istek sessizce yok sayilir (200 doner, mail gitmez).
 */
public record LeadRequest(
        @NotBlank @Pattern(regexp = "Satın al|Demo iste", message = "Geçersiz amaç") String amac,
        @NotBlank(message = "Ad Soyad zorunludur") @Size(max = 120) String ad,
        @NotBlank(message = "Kurum adı zorunludur") @Size(max = 160) String kurum,
        @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin")
        @Size(max = 160) String email,
        @Size(max = 40) String telefon,
        @Size(max = 200) String website) {

    public boolean botMu() {
        return website != null && !website.isBlank();
    }
}
