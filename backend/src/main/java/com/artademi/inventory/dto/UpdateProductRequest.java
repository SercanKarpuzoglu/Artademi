package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Urun guncelleme istegi. Stok ve aktiflik BURADA degismez: onlarin kendi uclari vardir
 * (PATCH /stok, PATCH /active). Grup guncellemesini aynalar (aktiflik haric).
 *
 * <p>PARA KURALI: {@code satisFiyati} {@link BigDecimal} ve pozitif olmalidir (@Positive -> 400).
 */
public record UpdateProductRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotNull(message = "Satış fiyatı zorunludur")
        @Positive(message = "Satış fiyatı pozitif olmalıdır")
        BigDecimal satisFiyati,

        String aciklama) {
}
