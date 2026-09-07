package com.artademi.kasa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Kasalar arasi transfer. Iki hareket satiri uretir (kaynakta CIKIS, hedefte GIRIS). */
public record TransferRequest(
        @NotNull(message = "Kaynak kasa zorunludur") Long kaynakKasaId,
        @NotNull(message = "Hedef kasa zorunludur") Long hedefKasaId,
        @NotNull(message = "Tutar zorunludur")
        @DecimalMin(value = "0.01", message = "Tutar sıfırdan büyük olmalıdır") BigDecimal tutar,
        LocalDate tarih,
        @Size(max = 500) String aciklama) {

    public LocalDate tarihOrBugun() {
        return tarih == null ? LocalDate.now() : tarih;
    }
}
