package com.artademi.branch.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Brans guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 */
public record UpdateBranchRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        String aciklama,
        /** Bransin varsayilan donemi (opsiyonel); yeni grup acilirken on-doldurulur. */
        Long donemId) {
}
