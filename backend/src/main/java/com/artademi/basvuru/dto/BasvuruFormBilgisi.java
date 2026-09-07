package com.artademi.basvuru.dto;

import java.util.List;

/**
 * Public formun acilis bilgisi — KIMLIKSIZ uctan doner.
 *
 * <p>⚠️ Bilincli olarak MINIMUM veri tasir: yalnizca kurum adi ve aktif brans adlari.
 * Ogrenci sayisi, iletisim bilgisi, fiyat gibi hicbir is verisi BURADAN SIZMAZ.
 * Brans adlari kurumun zaten kamuya duyurdugu bilgidir (web sitesinde yazar).
 */
public record BasvuruFormBilgisi(String kurumAdi, List<BransSecenegi> branslar) {

    /** Formdaki brans acilir listesi icin id + ad; baska alan TASIMAZ. */
    public record BransSecenegi(Long id, String ad) {
    }
}
