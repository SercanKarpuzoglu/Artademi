package com.artademi.kasa.dto;

import com.artademi.kasa.Kasa;
import com.artademi.kasa.KasaTipi;
import java.math.BigDecimal;

/**
 * Kasa yaniti. {@code bakiye} HESAPLANMIS degerdir — veritabaninda saklanmaz.
 *
 * @param bakiye acilis + tahsilatlar - giderler + hareket girisleri - hareket cikislari
 */
public record KasaResponse(Long id, String ad, KasaTipi tip, String iban,
        BigDecimal acilisBakiyesi, BigDecimal bakiye, boolean aktif) {

    public static KasaResponse from(Kasa k, BigDecimal bakiye) {
        return new KasaResponse(k.getId(), k.getAd(), k.getTip(), k.getIban(),
                k.getAcilisBakiyesi(), bakiye, k.isAktif());
    }
}
