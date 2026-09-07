package com.artademi.telafi.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Hakkin kullanildigini isaretler.
 *
 * @param kullanilanOturumId hangi derste kullanildi — KANIT olarak zorunlu; olmadan
 *     "kullanildi" demek izsiz kalirdi
 */
public record TelafiKullanRequest(
        @NotNull(message = "Telafinin yapıldığı ders zorunludur") Long kullanilanOturumId,
        LocalDate kullanimTarihi) {
}
