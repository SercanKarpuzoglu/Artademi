package com.artademi.kasa.dto;

import com.artademi.kasa.HareketYonu;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Elle kasa duzeltmesi (sayim farki, banka masrafi…).
 *
 * <p>Tutar HER ZAMAN pozitiftir; yon ayri alandadir. Negatif tutara izin verilseydi
 * "eksi giris" ile "arti cikis" ayni seyi iki bicimde ifade ederdi.
 */
public record DuzeltmeRequest(
        @NotNull(message = "Yön zorunludur") HareketYonu yon,
        @NotNull(message = "Tutar zorunludur")
        @DecimalMin(value = "0.01", message = "Tutar sıfırdan büyük olmalıdır") BigDecimal tutar,
        LocalDate tarih,
        @Size(max = 500) String aciklama) {

    public LocalDate tarihOrBugun() {
        return tarih == null ? LocalDate.now() : tarih;
    }
}
