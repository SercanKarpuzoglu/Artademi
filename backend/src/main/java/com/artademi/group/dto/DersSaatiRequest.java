package com.artademi.group.dto;

import com.artademi.schedule.HaftaGunu;
import com.artademi.schedule.dto.SaatAraligiGecerli;
import com.artademi.schedule.dto.SaatBilgisi;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** Grup olustururken satir ici ders saati (gun + aralik). Cakisma kontrolu ScheduleService'te, ayni islemde. */
@SaatAraligiGecerli
public record DersSaatiRequest(
        @NotNull(message = "Gün zorunludur") HaftaGunu gun,
        @NotNull(message = "Başlangıç saati zorunludur") LocalTime baslangicSaati,
        @NotNull(message = "Bitiş saati zorunludur") LocalTime bitisSaati) implements SaatBilgisi {
}
