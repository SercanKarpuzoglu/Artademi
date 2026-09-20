package com.artademi.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * Urun iadesi istegi (POST /api/sales/{id}/iade). tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code adet} POZITIF verilir ("2 adet iade al"); satira NEGATIF yazilir. Birim fiyat ORIJINAL
 * satistan kopyalanir — urunun fiyati sonradan degistiyse veliye satin aldigi fiyat geri verilir.
 *
 * <p>{@code kasaId} verilmezse orijinal satisin kasasi kullanilir (o da yoksa iade hicbir kasaya
 * islenmez — eski satislarin kasasi yoktur).
 */
public record SatisIadeRequest(
        @NotNull(message = "Adet zorunludur")
        @Positive(message = "İade adedi 0'dan büyük olmalıdır")
        Integer adet,

        LocalDate iadeTarihi,

        Long kasaId,

        String aciklama) {
}
