package com.artademi.inventory.dto;

import java.math.BigDecimal;

/**
 * Urun iadesi onay ozeti (GET /api/sales/{id}/iade-onizleme). {@code IadeOnizleme} ile ayni
 * gerekce: iade stogu ve kasayi degistirir, ne olacagi onceden gorunmeli.
 *
 * @param satisId         iade edilecek orijinal satis
 * @param satilanAdet     orijinal satis adedi
 * @param iadeEdilenAdet  bugune kadar iade edilmis adet (pozitif)
 * @param iadeEdilebilir  kalan iade siniri = satilan - iadeEdilen
 * @param birimFiyat      iadede kullanilacak birim fiyat (orijinal satistan)
 * @param engel           iade yapilamiyorsa sebebi; yapilabiliyorsa {@code null}
 */
public record SatisIadeOnizleme(
        Long satisId,
        int satilanAdet,
        int iadeEdilenAdet,
        int iadeEdilebilir,
        BigDecimal birimFiyat,
        String engel) {
}
