package com.artademi.report.dto;

import com.artademi.group.GrupTipi;

/**
 * Grup doluluk raporu satiri (RAPOR — salt okunur). Hicbir tenant_id alani SIZMAZ.
 *
 * @param grupId             grup id
 * @param ad                 grup adi
 * @param tip                grup tipi (GRUP / OZEL)
 * @param ogretmenAd         ogretmen tam adi (ad + soyad)
 * @param aktifOgrenciSayisi gruptaki AKTIF kayit sayisi
 */
public record GroupOccupancyRow(
        Long grupId,
        String ad,
        GrupTipi tip,
        String ogretmenAd,
        long aktifOgrenciSayisi) {
}
