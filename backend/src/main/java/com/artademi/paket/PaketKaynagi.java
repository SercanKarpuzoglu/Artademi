package com.artademi.paket;

/** Ders paketinin nereden dogdugu (V35). */
public enum PaketKaynagi {
    /** Finans > Ders Paketleri'nden elle satis. */
    ELLE,
    /** Donemlik kayit: donemdeki ders sayisi kadar kredi + tek tahakkuk. */
    KAYIT_DONEMLIK,
    /** Aylik kayit: Otomatik Tahakkuk ile o ayin ders sayisi kadar 0 TL kredi (aidat ayri tahakkuk). */
    AYLIK_KREDI
}
