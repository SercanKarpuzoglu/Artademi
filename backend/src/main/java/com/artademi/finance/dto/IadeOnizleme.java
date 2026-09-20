package com.artademi.finance.dto;

import java.math.BigDecimal;

/**
 * Iade ekraninin onaydan ONCE gosterdigi ozet (GET /api/payments/{id}/iade-onizleme).
 *
 * <p>Neden ayri bir uc: iade parayi kasadan cikarir VE ogrencinin kalan kredisini iptal eder.
 * Ikisini de yaptiktan sonra haber vermek gec kalir; {@code SilmeOnizleme} ile ayni gerekce.
 *
 * @param odemeId          iade edilecek orijinal tahsilat
 * @param odenenTutar      orijinal tahsilat tutari
 * @param iadeEdilenTutar  bugune kadar iade edilmis toplam (pozitif)
 * @param iadeEdilebilir   kalan iade siniri = odenen - iadeEdilen
 * @param iptalEdilecekPaket iade halinde iptal edilecek AKTIF paket sayisi
 * @param iptalEdilecekKontor iade halinde iptal edilecek kullanilmamis ders sayisi
 * @param engel            iade yapilamiyorsa sebebi; yapilabiliyorsa {@code null}
 */
public record IadeOnizleme(
        Long odemeId,
        BigDecimal odenenTutar,
        BigDecimal iadeEdilenTutar,
        BigDecimal iadeEdilebilir,
        int iptalEdilecekPaket,
        int iptalEdilecekKontor,
        String engel) {
}
