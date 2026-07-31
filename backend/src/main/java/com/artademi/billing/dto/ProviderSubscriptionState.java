package com.artademi.billing.dto;

import java.time.LocalDate;

/**
 * Saglayicidaki aboneligin GERCEK durumu — mutabakat (reconciliation) icin.
 *
 * <p>Neden gerekli: webhook teslimi garanti DEGIL (sandbox'ta hic gelmedigi olcumle goruldu;
 * canlida da ag kesintisi/gecici hata ile kacabilir). Webhook "hizli yol", bu sorgu ise
 * "dogruluk kaynagi": para ceklidiyse sistem er ya da gec bunu gorur ve odeme yapan kurumu
 * haksiz yere askiya almaz.
 *
 * @param aktif saglayicida abonelik aktif mi (iptal/askida degil)
 * @param sonOdemeBasarili en son tamamlanmis tahsilat basarili mi
 * @param odenmisDonemSonu basarili son tahsilatin kapsadigi donemin bitisi (null olabilir)
 */
public record ProviderSubscriptionState(
        boolean aktif,
        boolean sonOdemeBasarili,
        LocalDate odenmisDonemSonu) {

    /** Saglayicida bulunamayan/sorgulanamayan abonelik icin notr durum (mutabakat atlar). */
    public static ProviderSubscriptionState bilinmiyor() {
        return new ProviderSubscriptionState(false, false, null);
    }
}
