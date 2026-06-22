package com.artademi.dashboard.dto;

import java.util.List;

/**
 * FRONTDESK paneli — ⚠️ HICBIR PARASAL ALAN YOK. Yalnizca operasyonel ozet: aktif ogrenci/grup
 * sayilari, bugunku dersler, son ogrenciler. Tahsilat/gider/borc/odeme/trend hicbiri bu tipte
 * tanimli DEGIL → JSON'da o anahtarlar hic bulunmaz (tip-duzeyi filtre).
 */
public record FrontdeskDashboard(
        String rol,
        Sayilar sayilar,
        List<DersOzet> bugunDersler,
        List<OgrenciOzet> sonOgrenciler) implements DashboardData {

    public record Sayilar(long aktifOgrenci, long aktifGrup) {
    }
}
