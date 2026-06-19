package com.artademi.finance.dto;

import com.artademi.finance.Accrual;
import java.math.BigDecimal;

/**
 * Tahakkuk yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Referanslar tam DTO yerine ozet olarak donulur: ogrenci {id, ad, soyad}, grup {id, ad}.
 */
public record AccrualResponse(
        Long id,
        BigDecimal tutar,
        String donem,
        String aciklama,
        OgrenciRef ogrenci,
        GrupRef grup) {

    /** Ogrenci ozeti (id + ad + soyad). */
    public record OgrenciRef(Long id, String ad, String soyad) {
    }

    /** Grup ozeti (id + ad). */
    public record GrupRef(Long id, String ad) {
    }

    public static AccrualResponse from(Accrual a) {
        OgrenciRef ogrenci = a.getOgrenci() == null
                ? null
                : new OgrenciRef(a.getOgrenci().getId(), a.getOgrenci().getAd(), a.getOgrenci().getSoyad());
        GrupRef grup = a.getGrup() == null
                ? null
                : new GrupRef(a.getGrup().getId(), a.getGrup().getAd());
        return new AccrualResponse(
                a.getId(),
                a.getTutar(),
                a.getDonem(),
                a.getAciklama(),
                ogrenci,
                grup);
    }
}
