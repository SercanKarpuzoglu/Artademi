package com.artademi.attendance;

/**
 * Bir ogrencinin yoklama oturumundaki durumu. Oturum olusturulurken her ogrenci icin varsayilan
 * {@link #GELMEDI} atanir; toplu guncelleme ile degistirilir.
 */
public enum YoklamaDurumu {

    /** Ogrenci derse geldi. */
    GELDI,

    /** Ogrenci derse gelmedi (oturum olusturulurken varsayilan). */
    GELMEDI,

    /** Ogrenci izinli (mazeretli). */
    IZINLI
}
