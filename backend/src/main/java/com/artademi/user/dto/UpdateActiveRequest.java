package com.artademi.user.dto;

import jakarta.validation.constraints.NotNull;

/** Kullanici aktiflik degisikligi istegi (/api/users/{id}/active PATCH). */
public record UpdateActiveRequest(@NotNull Boolean aktif) {
}
