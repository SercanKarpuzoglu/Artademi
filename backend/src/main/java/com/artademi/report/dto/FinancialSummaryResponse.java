package com.artademi.report.dto;

import java.math.BigDecimal;

/**
 * Aylik finansal ozet raporu (RAPOR — salt okunur). Hicbir tenant_id alani SIZMAZ.
 *
 * <p>Tum parasal alanlar {@link BigDecimal}, scale 2 (HALF_UP). {@code net = toplamGelir -
 * toplamGider}.
 *
 * @param donem "YYYY-MM" donem
 * @param gelir gelir kalemleri (tahsilat + urun satis)
 * @param gider gider kalemleri (ofis gideri + hakedis)
 * @param net   net sonuc (gelir - gider)
 */
public record FinancialSummaryResponse(
        String donem,
        Gelir gelir,
        Gider gider,
        BigDecimal net) {

    /**
     * Gelir kalemleri.
     *
     * @param tahsilat    donemdeki ogrenci tahsilatlari toplami
     * @param urunSatis   donemdeki urun satis toplami
     * @param toplamGelir tahsilat + urunSatis
     */
    public record Gelir(
            BigDecimal tahsilat,
            BigDecimal urunSatis,
            BigDecimal toplamGelir) {
    }

    /**
     * Gider kalemleri.
     *
     * @param ofisGideri  donemdeki ofis/genel giderler toplami
     * @param hakedis     donemdeki ogretmen hakedisleri toplami
     * @param toplamGider ofisGideri + hakedis
     */
    public record Gider(
            BigDecimal ofisGideri,
            BigDecimal hakedis,
            BigDecimal toplamGider) {
    }
}
