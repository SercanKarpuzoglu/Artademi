package com.artademi.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Odeme entegrasyonu ayarlari ({@code artademi.billing.*}). Sirlar (api-key/secret-key) KODA
 * GOMULMEZ; yalnizca ortam degiskeninden gelir. Anahtarlar bos ise iyzico cagrilari calismaz
 * (checkout baslatma 409 verir) ama uygulama ayaga kalkar — dev/test'te saglayici mock'lanir.
 *
 * @param webReturnUrl checkout sonrasi kullanicinin yonlendirilecegi web sayfasi (SPA rotasi)
 * @param iyzico iyzico'ya ozgu ayarlar
 */
@ConfigurationProperties(prefix = "artademi.billing")
public record BillingProperties(String webReturnUrl, java.math.BigDecimal aylikPlanUcreti,
        Iyzico iyzico) {

    /**
     * Aylik plan ucreti (KDV haric) — platform genel bakisinda MRR hesabinda kullanilir.
     * Yapilandirilmamissa landing'de duyurulan tutar varsayilir.
     */
    public java.math.BigDecimal aylikPlanUcreti() {
        return aylikPlanUcreti == null ? new java.math.BigDecimal("10000") : aylikPlanUcreti;
    }

    /**
     * @param baseUrl sandbox: {@code https://sandbox-api.iyzipay.com}, prod: {@code https://api.iyzipay.com}
     * @param apiKey iyzico API anahtari (env: IYZICO_API_KEY)
     * @param secretKey iyzico gizli anahtari (env: IYZICO_SECRET_KEY) — webhook imzasi da bununla dogrulanir
     * @param merchantId iyzico satici id'si (webhook imza girdisi)
     * @param pricingPlanReferenceCode iyzico panelinde tanimli aylik planin referans kodu
     * @param callbackUrl checkout sonrasi iyzico'nun browser POST atacagi backend ucu
     */
    public record Iyzico(String baseUrl, String apiKey, String secretKey, String merchantId,
            String pricingPlanReferenceCode, String callbackUrl) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }
}
