package com.artademi.payout.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Hakedis hesaplama istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code ogretmenId} (zorunlu) serviste {@code teacherRepository.findScopedById} ile tenant-guvenli
 * dogrulanir (baska tenant'in / yok olan ogretmen -> 404). {@code donem} "YYYY-MM" formatinda olmalidir
 * (gecersizse serviste 400). {@code kdvOrani} opsiyonel; yalnizca CIRO_ORANI hesabinda kullanilir,
 * verilmezse serviste varsayilan 20 alinir.
 */
public record CalculatePayoutRequest(
        @NotNull(message = "Öğretmen zorunludur")
        Long ogretmenId,

        @NotNull(message = "Dönem zorunludur")
        String donem,

        BigDecimal kdvOrani) {
}
