package com.artademi.billing.dto;

/**
 * Baslatilan checkout oturumu.
 *
 * @param token saglayicinin checkout token'i (sonuc sorgusunda kullanilir)
 * @param checkoutFormContent web sayfasina gomulecek HTML/JS icerigi (iyzico hosted form)
 */
public record CheckoutSession(String token, String checkoutFormContent) {
}
