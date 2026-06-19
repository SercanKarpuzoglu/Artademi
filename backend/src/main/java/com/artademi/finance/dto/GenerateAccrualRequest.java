package com.artademi.finance.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Otomatik aylik tahakkuk uretimi istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code donem} format "YYYY-MM"; serviste YearMonth ile parse/normalize edilir, gecersizse
 * -&gt; 400 VALIDATION_ERROR.
 */
public record GenerateAccrualRequest(
        @NotNull(message = "Dönem zorunludur")
        String donem) {
}
