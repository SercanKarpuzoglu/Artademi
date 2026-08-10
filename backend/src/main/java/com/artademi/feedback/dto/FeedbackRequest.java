package com.artademi.feedback.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Uygulama icinden gonderilen geri bildirim.
 *
 * @param eposta opsiyonel — verilirse yanit dogrudan bu adrese gider (oturumdaki e-posta
 *               guncel olmayabilir; kullaniciya secme hakki birakilir)
 */
public record FeedbackRequest(
        @NotNull(message = "Geri bildirim tipi zorunludur") FeedbackTipi tip,
        @NotBlank(message = "Mesaj zorunludur")
        @Size(min = 10, max = 4000, message = "Mesaj en az 10, en fazla 4000 karakter olmalı")
        String mesaj,
        @Email(message = "Geçerli bir e-posta girin") String eposta) {
}
