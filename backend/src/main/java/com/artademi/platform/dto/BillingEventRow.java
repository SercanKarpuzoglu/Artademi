package com.artademi.platform.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Odeme hareketi satiri (billing_event) — webhook/mutabakat izi.
 *
 * @param status PROCESSED (islendi) / IGNORED (eslesmedi — orn. baska hesabin bildirimi)
 * @param kurumAdi eslesmeyen kayitlarda null olabilir
 */
public record BillingEventRow(
        UUID id,
        String provider,
        String eventType,
        String status,
        UUID tenantId,
        String kurumAdi,
        Instant createdAt) {
}
