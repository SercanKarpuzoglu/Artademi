package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * Satis olusturma istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code urunId} ZORUNLU; {@code ogrenciId} OPSIYONEL. Ikisi de (doluysa) serviste
 * {@code findScopedById} ile tenant-guvenli dogrulanir (baska tenant'in / yok olan referans -> 404).
 *
 * <p>{@code adet} pozitif olmalidir (@Positive -> 400). {@code satisTarihi} verilmezse bugun
 * kullanilir. {@code birimFiyat} ve {@code toplamTutar} istekte ALINMAZ: birimFiyat satis aninda
 * urunun guncel satisFiyati'ndan kopyalanir, toplamTutar = birimFiyat * adet (serviste hesaplanir).
 */
public record CreateSaleRequest(
        @NotNull(message = "Ürün zorunludur")
        Long urunId,

        Long ogrenciId,

        @NotNull(message = "Adet zorunludur")
        @Positive(message = "Adet pozitif olmalıdır")
        Integer adet,

        LocalDate satisTarihi,

        String aciklama) {
}
