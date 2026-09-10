package com.artademi.indirim.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Ogrenciye indirim atama. grupId NULL = tum gruplar; baslangic NULL = bugun; bitis NULL = surekli. */
public record OgrenciIndirimiRequest(
        @NotNull(message = "İndirim zorunludur") Long indirimId,
        Long grupId,
        LocalDate baslangic,
        LocalDate bitis,
        String aciklama) {
}
