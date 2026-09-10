package com.artademi.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Finans > Gelirler ozeti: tarih araligindaki ogrenci odemeleri + urun satislari. Toplamlar
 * saklanmaz, her istekte hesaplanir (scale 2 HALF_UP).
 */
public record GelirOzetiResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal odemeToplam,
        BigDecimal satisToplam,
        BigDecimal toplam) {
}
