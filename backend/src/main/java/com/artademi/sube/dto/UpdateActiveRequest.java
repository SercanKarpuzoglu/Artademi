package com.artademi.sube.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Aktiflik degisikligi istegi (PATCH /api/subeler/{id}/active).
 * Silme yerine pasiflestirme de bu endpoint uzerinden yapilir.
 */
public record UpdateActiveRequest(
        @NotNull(message = "Aktiflik zorunludur")
        Boolean aktif) {
}
