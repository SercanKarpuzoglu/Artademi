package com.artademi.kasa;

/**
 * Elle girilen kasa hareketinin sebebi.
 *
 * <p>Tahsilat ve gider BU TABLODA DEGILDIR — onlar zaten kendi tablolarinda duruyor ve
 * kasaya {@code kasa_id} ile bagli. Burada yalnizca onlarin disindaki hareketler tutulur;
 * aksi halde ayni para iki kez sayilirdi.
 */
public enum HareketTipi {

    /** Kasalar arasi aktarim. Iki satir uretir (kaynakta CIKIS, hedefte GIRIS). */
    TRANSFER,

    /** Sayim farki, banka masrafi gibi elle duzeltmeler. */
    DUZELTME
}
