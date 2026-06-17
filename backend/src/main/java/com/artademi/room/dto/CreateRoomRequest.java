package com.artademi.room.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Salon olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir,
 * yeni kayit her zaman aktif (true) baslar.
 */
public record CreateRoomRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        Integer kapasite,

        String aciklama) {
}
