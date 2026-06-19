package com.artademi.payout.dto;

import com.artademi.payout.Payout;
import com.artademi.payout.PayoutDurumu;
import com.artademi.payout.PayoutHesap;
import com.artademi.teacher.HakedisTipi;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Hakedis yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Ogretmen ozet olarak donulur (id, ad, soyad). {@code dokum}, tipe gore farkli alanlari dolu olan
 * hesaplama dokumunu tasir (SAATLIK: dersSayisi/birimUcret; CIRO_ORANI: toplamTahsilat/kdvOrani/
 * netCiro/oran).
 *
 * <p>Kaydedilmis hakediş icin {@link #from(Payout)}, kayitsiz onizleme icin {@link #from(PayoutHesap)}
 * kullanilir (onizlemede id/durum/odemeTarihi null).
 */
public record PayoutResponse(
        Long id,
        OgretmenRef ogretmen,
        String donem,
        HakedisTipi hakedisTipi,
        BigDecimal hesaplananTutar,
        PayoutDurumu durum,
        LocalDate odemeTarihi,
        Dokum dokum) {

    /** Ogretmen ozeti (id + ad + soyad). */
    public record OgretmenRef(Long id, String ad, String soyad) {
    }

    /**
     * Hesaplama dokumu. Tipe gore yalnizca ilgili alanlar doludur, digerleri null:
     * SAATLIK -> dersSayisi + birimUcret; CIRO_ORANI -> toplamTahsilat + kdvOrani + netCiro + oran.
     */
    public record Dokum(
            Integer dersSayisi,
            BigDecimal birimUcret,
            BigDecimal toplamTahsilat,
            BigDecimal kdvOrani,
            BigDecimal netCiro,
            BigDecimal oran) {
    }

    /** Kaydedilmis hakediş kaydindan yanit kurar. */
    public static PayoutResponse from(Payout p) {
        OgretmenRef ogretmen = p.getOgretmen() == null
                ? null
                : new OgretmenRef(p.getOgretmen().getId(), p.getOgretmen().getAd(),
                        p.getOgretmen().getSoyad());
        Dokum dokum = new Dokum(
                p.getDersSayisi(),
                p.getBirimUcret(),
                p.getToplamTahsilat(),
                p.getKdvOrani(),
                p.getNetCiro(),
                p.getOran());
        return new PayoutResponse(
                p.getId(),
                ogretmen,
                p.getDonem(),
                p.getHakedisTipi(),
                p.getHesaplananTutar(),
                p.getDurum(),
                p.getOdemeTarihi(),
                dokum);
    }

    /** Kayitsiz onizleme hesabindan yanit kurar (id/durum/odemeTarihi null). */
    public static PayoutResponse from(PayoutHesap h) {
        OgretmenRef ogretmen = h.ogretmen() == null
                ? null
                : new OgretmenRef(h.ogretmen().getId(), h.ogretmen().getAd(), h.ogretmen().getSoyad());
        Dokum dokum = new Dokum(
                h.dersSayisi(),
                h.birimUcret(),
                h.toplamTahsilat(),
                h.kdvOrani(),
                h.netCiro(),
                h.oran());
        return new PayoutResponse(
                null,
                ogretmen,
                h.donem(),
                h.hakedisTipi(),
                h.hesaplananTutar(),
                null,
                null,
                dokum);
    }
}
