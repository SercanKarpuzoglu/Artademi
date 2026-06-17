package com.artademi.branch.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Aktiflik degisikligi istegi (PATCH /api/branches/{id}/active).
 * Silme yerine pasiflestirme de bu endpoint uzerinden yapilir.
 */
public record UpdateActiveRequest(
        @NotNull(message = "Aktiflik zorunludur")
        Boolean aktif) {
}
