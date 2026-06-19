package com.artademi.inventory.dto;

import com.artademi.inventory.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Satis yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Referanslar tam DTO yerine ozet olarak donulur: urun {id, ad}, ogrenci {id, ad, soyad}
 * (satis ogrenciye bagli degilse null).
 *
 * <p>{@code birimFiyat} satis aninda kopyalanan fiyattir; urunun guncel satisFiyati'ndan FARKLI
 * olabilir (sonradan fiyat degismisse).
 */
public record SaleResponse(
        Long id,
        UrunRef urun,
        OgrenciRef ogrenci,
        int adet,
        BigDecimal birimFiyat,
        BigDecimal toplamTutar,
        LocalDate satisTarihi,
        String aciklama,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Urun ozeti (id + ad). */
    public record UrunRef(Long id, String ad) {
    }

    /** Ogrenci ozeti (id + ad + soyad). */
    public record OgrenciRef(Long id, String ad, String soyad) {
    }

    public static SaleResponse from(Sale s) {
        UrunRef urun = s.getUrun() == null
                ? null
                : new UrunRef(s.getUrun().getId(), s.getUrun().getAd());
        OgrenciRef ogrenci = s.getOgrenci() == null
                ? null
                : new OgrenciRef(s.getOgrenci().getId(), s.getOgrenci().getAd(), s.getOgrenci().getSoyad());
        return new SaleResponse(
                s.getId(),
                urun,
                ogrenci,
                s.getAdet(),
                s.getBirimFiyat(),
                s.getToplamTutar(),
                s.getSatisTarihi(),
                s.getAciklama(),
                s.getOlusturulmaTarihi(),
                s.getGuncellenmeTarihi());
    }
}
