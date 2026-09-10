package com.artademi.indirim.dto;

import com.artademi.indirim.IndirimTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Indirim tanimi olusturma/guncelleme. ORAN icin deger (0,100]; TUTAR icin > 0 (serviste). */
public record IndirimRequest(
        @NotBlank(message = "Ad zorunludur") String ad,
        @NotNull(message = "Tip zorunludur") IndirimTipi tip,
        @NotNull(message = "Değer zorunludur") @Positive(message = "Değer 0'dan büyük olmalıdır") BigDecimal deger,
        String aciklama) {
}
