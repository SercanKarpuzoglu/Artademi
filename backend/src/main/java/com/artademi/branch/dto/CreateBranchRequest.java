package com.artademi.branch.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Brans olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir,
 * yeni kayit her zaman aktif (true) baslar.
 */
public record CreateBranchRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        String aciklama) {
}
