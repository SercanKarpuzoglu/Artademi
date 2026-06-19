package com.artademi.schedule.dto;

import com.artademi.schedule.HaftaGunu;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Program guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 *
 * <p>{@code bitisSaati > baslangicSaati} kosulu {@link SaatAraligiGecerli} ile; grup referansi
 * serviste {@code findScopedById} ile tenant-guvenli dogrulanir. Cakisma kontrolu kendi kaydi
 * haric uygulanir.
 */
@SaatAraligiGecerli
public record UpdateScheduleRequest(
        @NotNull(message = "Grup zorunludur")
        Long grupId,

        @NotNull(message = "Gün zorunludur")
        HaftaGunu gun,

        @NotNull(message = "Başlangıç saati zorunludur")
        LocalTime baslangicSaati,

        @NotNull(message = "Bitiş saati zorunludur")
        LocalTime bitisSaati) implements SaatBilgisi {
}
