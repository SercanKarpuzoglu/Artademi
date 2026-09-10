package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Stok giris/cikis istegi (PATCH /api/products/{id}/stok-hareket). {@code miktar} pozitifse GIRIS,
 * negatifse CIKIS; 0 anlamsizdir (400). Sonuc stok negatife dusemez (400 VALIDATION_ERROR).
 * Mutlak atama icin ayri uc var (PATCH /{id}/stok); bu uc gunluk "5 geldi / 2 satildi" akisi icindir.
 */
public record StokHareketRequest(
        @NotNull(message = "Miktar zorunludur")
        Integer miktar,
        String aciklama) {
}
