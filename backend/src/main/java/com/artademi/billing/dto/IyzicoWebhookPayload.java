package com.artademi.billing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * iyzico abonelik webhook govdesi (docs.iyzico.com/en/advanced/webhook).
 * Bilinmeyen alanlar yok sayilir (iyzico yeni alan ekleyebilir).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IyzicoWebhookPayload(
        String iyziEventType,
        Long iyziEventTime,
        String iyziReferenceCode,
        String subscriptionReferenceCode,
        String orderReferenceCode,
        String customerReferenceCode) {
}
