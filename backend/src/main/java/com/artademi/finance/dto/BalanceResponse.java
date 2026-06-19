package com.artademi.finance.dto;

import java.math.BigDecimal;

/**
 * Ogrenci bakiye yaniti. tenant_id sizdirilmaz.
 *
 * <p>{@code bakiye} = {@code toplamTahakkuk} - {@code toplamOdeme} (pozitif = borc). Tum degerler
 * {@link BigDecimal}, scale 2 (HALF_UP). PARA KURALI: asla double/float.
 */
public record BalanceResponse(
        Long ogrenciId,
        BigDecimal toplamTahakkuk,
        BigDecimal toplamOdeme,
        BigDecimal bakiye) {
}
