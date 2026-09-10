package com.artademi.donem.dto;

import com.artademi.donem.Donem;
import java.time.LocalDate;

public record DonemResponse(Long id, String ad, LocalDate baslangic, LocalDate bitis, boolean aktif) {
    public static DonemResponse from(Donem d) {
        return new DonemResponse(d.getId(), d.getAd(), d.getBaslangic(), d.getBitis(), d.isAktif());
    }
}
