package com.artademi.finance.dto;

import com.artademi.finance.OdemeYontemi;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahsilat iadesi istegi (POST /api/payments/{id}/iade). tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code tutar} POZITIF verilir ("500 TL iade et"); satira NEGATIF yazilir. Boylece arayuz
 * eksi isaretiyle ugrasmaz ve "-500 gonderdim, 500 iade oldu" gibi isaret karisikligi olmaz.
 *
 * <p>{@code odemeYontemi} verilmezse orijinal tahsilatin yontemi, {@code kasaId} verilmezse
 * orijinalin kasasi kullanilir — nakit alinip havaleyle iade edilebildigi icin ikisi de
 * degistirilebilir birakildi.
 */
public record IadeRequest(
        @NotNull(message = "İade tutarı zorunludur")
        @Positive(message = "İade tutarı 0'dan büyük olmalıdır")
        BigDecimal tutar,

        LocalDate iadeTarihi,

        OdemeYontemi odemeYontemi,

        Long kasaId,

        String aciklama) {
}
