package com.artademi.kasa.dto;

import com.artademi.kasa.HareketTipi;
import com.artademi.kasa.HareketYonu;
import com.artademi.kasa.KasaHareketi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Kasa hareketi yaniti (transfer/duzeltme). */
public record HareketResponse(Long id, Long kasaId, HareketYonu yon, HareketTipi tip,
        BigDecimal tutar, LocalDate tarih, String aciklama, UUID transferGrubu) {

    public static HareketResponse from(KasaHareketi h) {
        return new HareketResponse(h.getId(), h.getKasa().getId(), h.getYon(), h.getTip(),
                h.getTutar(), h.getTarih(), h.getAciklama(), h.getTransferGrubu());
    }
}
