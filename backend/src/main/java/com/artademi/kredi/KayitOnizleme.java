package com.artademi.kredi;

import com.artademi.enrollment.OdemePlani;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gruba yazarken plan secim ekrani icin hesap: "Donem 14 Eyl-31 Oca · 22 ders · 9.000 ₺" / "Bu ay 4 ders · 2.000 ₺".
 * {@code uygun=false} ise neden (grubun donemi yok, donem bitmis, donemlik ucret girilmemis…).
 */
public record KayitOnizleme(
        OdemePlani plan,
        boolean uygun,
        String neden,
        Long donemId,
        String donemAd,
        LocalDate baslangic,
        LocalDate bitis,
        int haftalikDers,
        int dersSayisi,
        BigDecimal ucret) {

    public static KayitOnizleme uygunDegil(OdemePlani plan, String neden) {
        return new KayitOnizleme(plan, false, neden, null, null, null, null, 0, 0, null);
    }
}
