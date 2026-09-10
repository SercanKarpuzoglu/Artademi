package com.artademi.indirim.dto;

import com.artademi.indirim.IndirimTanimi;
import com.artademi.indirim.IndirimTipi;
import java.math.BigDecimal;

public record IndirimResponse(Long id, String ad, IndirimTipi tip, BigDecimal deger, String etiket,
        String aciklama, boolean aktif) {
    public static IndirimResponse from(IndirimTanimi i) {
        return new IndirimResponse(i.getId(), i.getAd(), i.getTip(), i.getDeger(), i.etiket(), i.getAciklama(),
                i.isAktif());
    }
}
