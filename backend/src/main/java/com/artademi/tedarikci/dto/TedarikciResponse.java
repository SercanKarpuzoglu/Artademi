package com.artademi.tedarikci.dto;

import com.artademi.tedarikci.Tedarikci;
import java.math.BigDecimal;

/**
 * Tedarikci yaniti.
 *
 * @param toplamOdenen bu tedarikciye yapilan gider toplami — HESAPLANMIS, saklanmaz
 */
public record TedarikciResponse(Long id, String ad, String telefon, String email,
        String vergiNo, String aciklama, boolean aktif, BigDecimal toplamOdenen) {

    public static TedarikciResponse from(Tedarikci t, BigDecimal toplamOdenen) {
        return new TedarikciResponse(t.getId(), t.getAd(), t.getTelefon(), t.getEmail(),
                t.getVergiNo(), t.getAciklama(), t.isAktif(), toplamOdenen);
    }
}
