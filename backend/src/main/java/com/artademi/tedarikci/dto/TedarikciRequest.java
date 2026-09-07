package com.artademi.tedarikci.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tedarikci olusturma/guncelleme. */
public record TedarikciRequest(
        @NotBlank(message = "Tedarikçi adı zorunludur") @Size(max = 200) String ad,
        @Size(max = 30) String telefon,
        @Email(message = "Geçerli bir e-posta girin") @Size(max = 255) String email,
        @Size(max = 20) String vergiNo,
        @Size(max = 500) String aciklama) {
}
