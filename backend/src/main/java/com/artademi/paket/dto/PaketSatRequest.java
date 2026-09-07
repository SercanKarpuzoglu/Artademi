package com.artademi.paket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ders paketi satisi.
 *
 * <p>Satista PESIN tek tahakkuk uretilir; taksit istenirse kurum tahakkuku elle boler.
 * Otomatik taksit "kac taksit, hangi tarihlerde" gibi kurumdan kuruma degisen kurallar
 * gerektirir — kapsam disi.
 *
 * @param grupId opsiyonel; doluysa kontor once bu grubun derslerinden duser
 * @param sonKullanmaTarihi NULL = suresiz
 */
public record PaketSatRequest(
        @NotNull(message = "Öğrenci zorunludur") Long ogrenciId,
        @NotBlank(message = "Paket adı zorunludur") @Size(max = 150) String ad,
        Long grupId,
        @NotNull(message = "Ders sayısı zorunludur")
        @Min(value = 1, message = "Ders sayısı en az 1 olmalıdır") Integer toplamDers,
        @NotNull(message = "Tutar zorunludur")
        @DecimalMin(value = "0.00", message = "Tutar negatif olamaz") BigDecimal tutar,
        LocalDate satisTarihi,
        LocalDate sonKullanmaTarihi,
        @Size(max = 500) String aciklama) {

    public LocalDate satisTarihiOrBugun() {
        return satisTarihi == null ? LocalDate.now() : satisTarihi;
    }
}
