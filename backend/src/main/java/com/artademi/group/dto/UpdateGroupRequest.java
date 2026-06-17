package com.artademi.group.dto;

import com.artademi.group.GrupTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Grup guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 *
 * <p>Tipe gore salon/ucret zorunlulugu {@link GrupTutarli} ile; brans/ogretmen/salon referanslari
 * serviste {@code findScopedById} ile tenant-guvenli dogrulanir.
 */
@GrupTutarli
public record UpdateGroupRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotNull(message = "Tip zorunludur")
        GrupTipi tip,

        @NotNull(message = "Branş zorunludur")
        Long bransId,

        @NotNull(message = "Öğretmen zorunludur")
        Long ogretmenId,

        Long salonId,
        String seviye,
        BigDecimal aylikAidat,
        BigDecimal dersBasiUcret) implements GrupBilgisi {
}
