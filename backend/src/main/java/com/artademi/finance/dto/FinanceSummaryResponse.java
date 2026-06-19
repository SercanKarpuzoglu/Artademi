package com.artademi.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ogrenci finans ozeti: tahakkuk + tahsilat listeleri ve guncel bakiye. tenant_id sizdirilmaz.
 *
 * <p>{@code bakiye} = SUM(tahakkuklar) - SUM(odemeler), {@link BigDecimal} scale 2 (HALF_UP).
 */
public record FinanceSummaryResponse(
        Long ogrenciId,
        List<AccrualResponse> tahakkuklar,
        List<PaymentResponse> odemeler,
        BigDecimal bakiye) {
}
