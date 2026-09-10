package com.artademi.indirim.dto;

import com.artademi.indirim.OgrenciIndirimi;
import java.time.LocalDate;

public record OgrenciIndirimiResponse(Long id, IndirimResponse indirim, Long grupId, String grupAd,
        LocalDate baslangic, LocalDate bitis, String aciklama, boolean aktif) {
    public static OgrenciIndirimiResponse from(OgrenciIndirimi o) {
        return new OgrenciIndirimiResponse(o.getId(), IndirimResponse.from(o.getIndirim()),
                o.getGrup() == null ? null : o.getGrup().getId(),
                o.getGrup() == null ? null : o.getGrup().getAd(),
                o.getBaslangic(), o.getBitis(), o.getAciklama(), o.isAktif());
    }
}
