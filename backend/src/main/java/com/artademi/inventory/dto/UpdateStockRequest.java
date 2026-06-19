package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Stok guncelleme istegi (PATCH /api/products/{id}/stok).
 * MUTLAK ATAMA: {@code stokAdedi} dogrudan urunun yeni stok degeri olur (artirma/azaltma DEGIL).
 * Negatif olamaz (@PositiveOrZero -> 400).
 */
public record UpdateStockRequest(
        @NotNull(message = "Stok adedi zorunludur")
        @PositiveOrZero(message = "Stok adedi negatif olamaz")
        Integer stokAdedi) {
}
