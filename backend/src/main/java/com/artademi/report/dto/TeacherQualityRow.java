package com.artademi.report.dto;

import java.math.BigDecimal;

/**
 * Egitmen kalitesi satiri (Dalga F): yuk (aktif grup, ogrenci, haftalik ders saati), tarih araliginda
 * planlanan ders (program x takvim) vs alinan yoklama, kaydedilmemis oturum, katilim orani.
 */
public record TeacherQualityRow(
        Long ogretmenId,
        String ad,
        String soyad,
        long aktifGrup,
        long ogrenciSayisi,
        BigDecimal haftalikDersSaati,
        int planlananDers,
        long oturumSayisi,
        long alinmayanYoklama,
        long kaydedilmemisOturum,
        long geldi,
        long gelmedi,
        long izinli,
        BigDecimal katilimOrani) {
}
