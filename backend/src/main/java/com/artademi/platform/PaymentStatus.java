package com.artademi.platform;

/**
 * Odeme durumu. Odeme entegrasyonu YOK; bu deger elle/uctan set edilir (iyzico/PayTR ayri faz).
 * {@code evaluate} ve {@code markPaid} geçişleri bu degere bakar ({@link #ODENDI} -> erisim/telafi).
 */
public enum PaymentStatus {
    BEKLIYOR,
    ODENDI,
    BASARISIZ
}
