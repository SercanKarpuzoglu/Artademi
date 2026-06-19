package com.artademi.finance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Tahakkuk olusturma istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code ogrenciId} (zorunlu) ve varsa {@code grupId} serviste {@code findScopedById} ile
 * tenant-guvenli dogrulanir (baska tenant'in / yok olan referans -> 404).
 *
 * <p>PARA KURALI: {@code tutar} {@link BigDecimal} ve pozitif olmalidir ({@code @Positive} -> 400
 * VALIDATION_ERROR, error.fields.tutar). {@code donem} format "YYYY-MM" (opsiyonel).
 */
public record CreateAccrualRequest(
        @NotNull(message = "Öğrenci zorunludur")
        Long ogrenciId,

        Long grupId,

        String donem,

        @NotNull(message = "Tutar zorunludur")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal tutar,

        String aciklama) {
}
