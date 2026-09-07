package com.artademi.kasa;

/**
 * Kasa hareketinin yonu.
 *
 * <p>Tutar HER ZAMAN pozitiftir; isaret bu alanda tasinir. Negatif tutara izin verilseydi
 * "eksi giris" ile "arti cikis" ayni seyi iki farkli sekilde ifade eder, raporlar caprazlanirdi.
 */
public enum HareketYonu {
    GIRIS,
    CIKIS
}
