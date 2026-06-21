package com.artademi.platform.dto;

import com.artademi.platform.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Abonelik odeme/donem guncelleme istegi (SUPER_ADMIN; iyzico gelene kadar elle). {@code ODENDI}
 * verildiginde telafi calisir (abonelik AKTIF + tenant ASKIDA ise AKTIF). {@code currentPeriodEnd}
 * verilirse donem ilerletilir (opsiyonel).
 */
public record UpdateSubscriptionRequest(
        @NotNull(message = "Ödeme durumu zorunludur")
        PaymentStatus paymentStatus,

        LocalDate currentPeriodEnd) {
}
