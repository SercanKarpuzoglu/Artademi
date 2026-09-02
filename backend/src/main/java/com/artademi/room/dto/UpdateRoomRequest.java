package com.artademi.room.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Salon guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 */
public record UpdateRoomRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        Integer kapasite,

        /** Salonun bulundugu sube — OPSIYONEL (tek subeli kurum bos birakir). */
        Long subeId,

        String aciklama) {
}
