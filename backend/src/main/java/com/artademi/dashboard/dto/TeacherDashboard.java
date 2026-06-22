package com.artademi.dashboard.dto;

import com.artademi.group.GrupTipi;
import java.time.LocalDate;
import java.util.List;

/**
 * TEACHER paneli — yalnizca KENDI verisi (CurrentTeacherResolver izolasyonu). Parasal alan YOK.
 * Kendi gruplari (ogrenci sayisiyla), bugunku dersleri, son yoklamalari.
 */
public record TeacherDashboard(
        String rol,
        List<Grup> kendiGruplar,
        List<DersOzet> bugunDersler,
        List<Yoklama> sonYoklamalar) implements DashboardData {

    public record Grup(Long id, String ad, GrupTipi tip, long ogrenciSayisi) {
    }

    public record Yoklama(String grupAd, LocalDate tarih, long gelenSayi, long toplam) {
    }
}
