package com.artademi.report.dto;

import java.math.BigDecimal;

/**
 * Ogrenci bakiye raporu satiri (RAPOR — salt okunur). Hicbir tenant_id alani SIZMAZ.
 *
 * <p>{@code bakiye = toplamTahakkuk - toplamOdeme}; tum parasal alanlar {@link BigDecimal}, scale 2
 * (HALF_UP).
 *
 * @param ogrenciId      ogrenci id
 * @param ad             ogrenci adi
 * @param soyad          ogrenci soyadi
 * @param toplamTahakkuk toplam tahakkuk
 * @param toplamOdeme    toplam tahsilat
 * @param bakiye         tahakkuk - tahsilat (pozitif = borclu)
 */
public record StudentBalanceRow(
        Long ogrenciId,
        String ad,
        String soyad,
        BigDecimal toplamTahakkuk,
        BigDecimal toplamOdeme,
        BigDecimal bakiye) {
}
