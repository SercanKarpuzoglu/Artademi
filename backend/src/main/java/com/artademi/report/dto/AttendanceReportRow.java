package com.artademi.report.dto;

import java.math.BigDecimal;

/**
 * Devamsizlik raporu satiri: bir ogrencinin verilen tarih araligindaki katilim ozeti.
 *
 * @param toplamDers ogrencinin kayitli oldugu gruplarda acilan oturum sayisi
 * @param katilimOrani yuzde (0-100), 2 basamak; toplamDers=0 ise 0
 */
public record AttendanceReportRow(
        Long ogrenciId,
        String ogrenciAdSoyad,
        long toplamDers,
        long geldi,
        long gelmedi,
        long izinli,
        BigDecimal katilimOrani) {
}
