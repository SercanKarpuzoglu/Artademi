package com.artademi.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Parola degistirme istegi (/api/me/change-password POST). */
public record ChangePasswordRequest(
        @NotBlank String mevcutParola,
        @NotBlank String yeniParola) {
}
