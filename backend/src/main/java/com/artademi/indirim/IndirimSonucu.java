package com.artademi.indirim;

import java.math.BigDecimal;

/** Tahakkuk icin hesaplanmis indirim: brut, indirim, net ve insana okunur aciklama ("Kardeş %15, Burs 500 ₺"). */
public record IndirimSonucu(BigDecimal brut, BigDecimal indirim, BigDecimal net, String aciklama) {

    public static IndirimSonucu yok(BigDecimal brut) {
        return new IndirimSonucu(brut, BigDecimal.ZERO.setScale(2), brut, null);
    }

    public boolean var() {
        return indirim != null && indirim.signum() > 0;
    }
}
