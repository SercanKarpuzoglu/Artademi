package com.artademi.telafi;

/**
 * Telafi hakkinin durumu.
 *
 * <p>⚠️ {@code SURESI_DOLDU} DIYE BIR DURUM YOKTUR — bilincli. Olsaydi onu her gece
 * guncelleyen ayri bir job gerekirdi ve job kacarsa durum yalan soylerdi. Sure dolup
 * dolmadigi {@code son_kullanma_tarihi} uzerinden okuma aninda hesaplanir.
 */
public enum TelafiDurumu {

    /** Kullanilmayi bekliyor. */
    BEKLIYOR,

    /** Bir derste kullanildi; {@code kullanilan_oturum_id} dolar. */
    KULLANILDI,

    /** Kurum hakki geri aldi. */
    IPTAL
}
