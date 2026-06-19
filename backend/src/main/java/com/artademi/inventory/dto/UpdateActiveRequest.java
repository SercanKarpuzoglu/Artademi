package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Aktiflik degisikligi istegi (PATCH /api/products/{id}/active).
 * Silme yerine pasiflestirme de bu endpoint uzerinden yapilir.
 */
public record UpdateActiveRequest(
        @NotNull(message = "Aktiflik zorunludur")
        Boolean aktif) {
}
