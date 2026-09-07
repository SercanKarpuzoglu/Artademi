package com.artademi.paket;

/**
 * Paket durumu.
 *
 * <p>⚠️ {@code BITTI} DIYE BIR DURUM YOKTUR — bilincli. Kalan ders sayisi hesaplanan bir
 * degerdir; ayrica "bitti" durumu tutmak, yoklama duzeltmesiyle kontor geri geldiginde
 * durumu da geri almayi gerektirirdi ve o adim kacarsa durum yalan soylerdi.
 */
public enum PaketDurumu {

    /** Kullanilabilir. */
    AKTIF,

    /** Kurum paketi iptal etti. Kontor dusumu artik bu paketten yapilmaz. */
    IPTAL
}
