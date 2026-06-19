package com.artademi.schedule.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Aktiflik degisikligi istegi (PATCH /api/schedules/{id}/active).
 * Silme yerine pasiflestirme de bu endpoint uzerinden yapilir.
 */
public record UpdateActiveRequest(
        @NotNull(message = "Aktiflik zorunludur")
        Boolean aktif) {
}
