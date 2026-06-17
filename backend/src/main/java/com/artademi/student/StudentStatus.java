package com.artademi.student;

/**
 * Ogrenci statusu. Yeni kayit varsayilan {@link #DENEME} ile baslar; statu
 * degisikligi yalnizca PATCH /api/students/{id}/status ile manuel yapilir.
 */
public enum StudentStatus {

    /** Aktif (kayitli, devam eden) ogrenci. */
    AKTIF,

    /** Pasif ogrenci. Silme yerine bu statu kullanilir (veri korunur). */
    PASIF,

    /** Deneme dersi / deneme surecindeki ogrenci. Yeni kaydin varsayilani. */
    DENEME,

    /** Kaydi gecici olarak dondurulmus ogrenci. */
    DONDURULMUS
}
