package com.artademi.finance.dto;

import com.artademi.finance.OdemeYontemi;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahsilat olusturma istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code ogrenciId} (zorunlu) ve varsa {@code accrualId}/{@code grupId} serviste
 * {@code findScopedById} ile tenant-guvenli dogrulanir (baska tenant'in / yok olan referans -> 404).
 * accrualId verilirse tahakkugun ogrencisi payment ogrencisi ile AYNI olmalidir (-> 400).
 *
 * <p>PARA KURALI: {@code tutar} {@link BigDecimal} ve pozitif olmalidir ({@code @Positive} -> 400).
 * {@code odemeTarihi} opsiyonel; verilmezse serviste bugun (LocalDate.now()) kullanilir.
 * {@code odemeYontemi} zorunlu.
 */
public record CreatePaymentRequest(
        @NotNull(message = "Öğrenci zorunludur")
        Long ogrenciId,

        Long accrualId,

        Long grupId,

        @NotNull(message = "Tutar zorunludur")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal tutar,

        LocalDate odemeTarihi,

        @NotNull(message = "Ödeme yöntemi zorunludur")
        OdemeYontemi odemeYontemi,

        String aciklama) {
}
