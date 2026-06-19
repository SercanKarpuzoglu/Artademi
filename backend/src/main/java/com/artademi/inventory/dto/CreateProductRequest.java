package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Urun olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit her zaman
 * aktif (true) baslar.
 *
 * <p>PARA KURALI: {@code satisFiyati} {@link BigDecimal} ve pozitif olmalidir (@Positive -> 400).
 * {@code stokAdedi} opsiyonel (null gelirse 0 kabul edilir) ve negatif olamaz (@PositiveOrZero).
 */
public record CreateProductRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotNull(message = "Satış fiyatı zorunludur")
        @Positive(message = "Satış fiyatı pozitif olmalıdır")
        BigDecimal satisFiyati,

        @PositiveOrZero(message = "Stok adedi negatif olamaz")
        Integer stokAdedi,

        String aciklama) {
}
