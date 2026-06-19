package com.artademi.finance.dto;

import com.artademi.finance.OdemeYontemi;
import com.artademi.finance.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahsilat yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Referanslar tam DTO yerine ozet olarak donulur: ogrenci {id, ad, soyad}, grup {id, ad},
 * accrual {id, tutar, donem}.
 */
public record PaymentResponse(
        Long id,
        BigDecimal tutar,
        LocalDate odemeTarihi,
        OdemeYontemi odemeYontemi,
        String aciklama,
        OgrenciRef ogrenci,
        GrupRef grup,
        AccrualRef accrual) {

    /** Ogrenci ozeti (id + ad + soyad). */
    public record OgrenciRef(Long id, String ad, String soyad) {
    }

    /** Grup ozeti (id + ad). */
    public record GrupRef(Long id, String ad) {
    }

    /** Tahakkuk ozeti (id + tutar + donem). */
    public record AccrualRef(Long id, BigDecimal tutar, String donem) {
    }

    public static PaymentResponse from(Payment p) {
        OgrenciRef ogrenci = p.getOgrenci() == null
                ? null
                : new OgrenciRef(p.getOgrenci().getId(), p.getOgrenci().getAd(), p.getOgrenci().getSoyad());
        GrupRef grup = p.getGrup() == null
                ? null
                : new GrupRef(p.getGrup().getId(), p.getGrup().getAd());
        AccrualRef accrual = p.getAccrual() == null
                ? null
                : new AccrualRef(p.getAccrual().getId(), p.getAccrual().getTutar(), p.getAccrual().getDonem());
        return new PaymentResponse(
                p.getId(),
                p.getTutar(),
                p.getOdemeTarihi(),
                p.getOdemeYontemi(),
                p.getAciklama(),
                ogrenci,
                grup,
                accrual);
    }
}
