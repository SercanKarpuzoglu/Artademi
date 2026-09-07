package com.artademi.basvuru;

/**
 * Basvurunun kurum tarafindaki takip durumu. Basvuru SILINMEZ; durumu degisir
 * (kimin ne zaman basvurdugunun izi korunur).
 */
public enum BasvuruDurumu {

    /** Yeni geldi, kurum henuz ilgilenmedi — liste varsayilani. */
    YENI,

    /** Kurum ulasti/gorustu, sonuc henuz belli degil. */
    ARANDI,

    /** Ogrenci kaydina donusturuldu ({@code ogrenci_id} dolar). */
    OGRENCIYE_DONUSTU,

    /** Olumsuz sonuclandi (ilgilenmedi, uygun degil vb.). */
    OLUMSUZ
}
