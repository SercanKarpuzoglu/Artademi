package com.artademi.billing.notify;

/**
 * Kuruma gonderilen odeme/abonelik uyarilari. Her tip, bir abonelik DONEMI icinde EN FAZLA BIR KEZ
 * gonderilir (bkz. V19 {@code uq_billing_notification}).
 *
 * <p>Sıra: tahsilat basarisiz → ek sure basladi → ek sure bitmek uzere → askiya alindi.
 * Amac, kurumun bir sabah aniden panele giremediginde durumu ogrenmesini ONLEMEK.
 */
public enum BildirimTipi {

    /** Tahsilat denemesi basarisiz oldu (kart limiti/son kullanma vb.). */
    ODEME_BASARISIZ,
    /** Donem bitti, odeme alinamadi; ek sure (grace) basladi — erisim ACIK. */
    GRACE_BASLADI,
    /** Ek sure bitmek uzere (son birkac gun) — son uyari. */
    GRACE_BITIYOR,
    /** Ek sure doldu, hesap askiya alindi — erisim KAPALI. */
    ASKIYA_ALINDI
}
