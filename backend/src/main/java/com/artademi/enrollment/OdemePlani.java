package com.artademi.enrollment;

/** Kayit odeme plani (Dalga E). NULL (eski kayitlar) = AYLIK. */
public enum OdemePlani {
    /** Her ay aidat tahakkuku + o ayin ders sayisi kadar kredi (Otomatik Tahakkuk ile). */
    AYLIK,
    /** Donemlik ucret tek tahakkuk, donemdeki ders sayisi kadar kredi kayit aninda. */
    DONEMLIK,
    /**
     * Deneme dersi (urun karari 2026-09-12): para yok, kredi yok, ogrenci DENEME statusunde kalir, yoklama
     * alinabilir. "Plana gecir" ile AYLIK/DONEMLIK'e cevrilir ve o anda AKTIF olur.
     */
    DENEME
}
