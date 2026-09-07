package com.artademi.kasa.dto;

import com.artademi.kasa.KasaTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Kasa olusturma/guncelleme. */
public record KasaRequest(
        @NotBlank(message = "Kasa adı zorunludur") @Size(max = 150) String ad,
        @NotNull(message = "Kasa tipi zorunludur") KasaTipi tip,
        @Size(max = 34, message = "IBAN en fazla 34 karakter olabilir") String iban,
        BigDecimal acilisBakiyesi) {

    /** Acilis bakiyesi verilmemisse sifir. */
    public BigDecimal acilisBakiyesiOrZero() {
        return acilisBakiyesi == null ? BigDecimal.ZERO : acilisBakiyesi;
    }
}
