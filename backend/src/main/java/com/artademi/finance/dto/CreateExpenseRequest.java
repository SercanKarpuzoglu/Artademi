package com.artademi.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gider olusturma istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>PARA KURALI: {@code tutar} {@link BigDecimal} ve pozitif olmalidir ({@code @Positive} -> 400).
 * {@code giderTarihi} opsiyonel; verilmezse serviste bugun (LocalDate.now()) kullanilir.
 * {@code kategori} serbest metin (opsiyonel).
 */
public record CreateExpenseRequest(
        @NotNull(message = "Tutar zorunludur")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal tutar,

        LocalDate giderTarihi,

        String kategori,

        String aciklama) {
}
