package com.artademi.billing.dto;

/**
 * Saglayicidan dogrulanmis checkout sonucu.
 *
 * @param success odeme/abonelik kurulumu basarili mi
 * @param subscriptionReferenceCode saglayicidaki abonelik referansi (webhook eslesmesi bununla)
 * @param customerReferenceCode saglayicidaki musteri referansi (olabilir null)
 */
public record CheckoutResult(boolean success, String subscriptionReferenceCode,
        String customerReferenceCode) {
}
