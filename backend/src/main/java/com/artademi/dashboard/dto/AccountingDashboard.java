package com.artademi.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * FRONTDESK_ACCOUNTING paneli — tahsilat + borc gorur, ama GIDER/NET ve hakedis GORMEZ (ofis gideri
 * admin isi). Trend yalnizca tahsilat. Grace uyarisi yok (yalniz ADMIN'e).
 */
public record AccountingDashboard(
        String rol,
        Sayilar sayilar,
        List<Trend> trend6Ay,
        List<OdemeOzet> sonOdemeler,
        List<OgrenciOzet> sonOgrenciler,
        List<DersOzet> bugunDersler) implements DashboardData {

    public record Sayilar(
            long aktifOgrenci,
            long aktifGrup,
            BigDecimal buAyTahsilat,
            BigDecimal bekleyenBorcToplam) {
    }

    public record Trend(String donem, BigDecimal tahsilat) {
    }
}
