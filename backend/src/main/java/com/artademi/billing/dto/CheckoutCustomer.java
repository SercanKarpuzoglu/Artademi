package com.artademi.billing.dto;

/**
 * Checkout baslatmak icin saglayiciya gereken musteri bilgisi. iyzico abonelik API'si ad/soyad,
 * e-posta, GSM, kimlik/vergi no ve fatura adresini ZORUNLU tutar (kurumsal musteri icin VKN girilir).
 */
public record CheckoutCustomer(
        String ad,
        String soyad,
        String email,
        String telefon,
        String kimlikVergiNo,
        String adres,
        String sehir,
        String ulke) {
}
