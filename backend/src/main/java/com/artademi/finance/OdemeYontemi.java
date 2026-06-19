package com.artademi.finance;

/**
 * Odeme (tahsilat) yontemi. Tahsilat olusturulurken zorunludur; VARCHAR(20) olarak saklanir.
 */
public enum OdemeYontemi {

    /** Nakit tahsilat. */
    NAKIT,

    /** Kredi/banka karti ile tahsilat. */
    KART,

    /** Banka havalesi/EFT ile tahsilat. */
    HAVALE
}
