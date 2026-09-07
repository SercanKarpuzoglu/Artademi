package com.artademi.basvuru.dto;

import com.artademi.basvuru.BasvuruDurumu;
import jakarta.validation.constraints.NotNull;

/** Basvuru durumu guncelleme (ARANDI / OLUMSUZ vb.). */
public record DurumGuncelleRequest(@NotNull(message = "Durum zorunludur") BasvuruDurumu durum) {
}
