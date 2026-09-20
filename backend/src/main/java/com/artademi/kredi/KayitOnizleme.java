package com.artademi.kredi;

import com.artademi.enrollment.OdemePlani;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gruba yazarken plan secim ekrani icin hesap: "Donem 14 Eyl-31 Oca · 22 ders · 9.000 ₺" / "Bu ay 4 ders · 2.000 ₺".
 * {@code uygun=false} ise neden (grubun donemi yok, donem bitmis, donemlik ucret girilmemis…).
 *
 * @param dersSayisi      kayit tarihinden donem/ay sonuna kalan ders — acilacak kontor sayisi
 * @param donemToplamDers donemin TAMAMINDAKI ders (yalniz DONEMLIK'te dolu); orantilama paydasi.
 *                        Arayuz "22 dersin 11'i" diyebilsin diye doner.
 * @param tamUcret        grubun ilan edilen donemlik ucreti (orantilanmamis); yalniz DONEMLIK'te dolu
 * @param ucret           ODENECEK tutar — donem ortasinda kayitta orantilanmis haldedir (§7.39)
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
        int donemToplamDers,
        BigDecimal tamUcret,
        BigDecimal ucret) {

    public static KayitOnizleme uygunDegil(OdemePlani plan, String neden) {
        return new KayitOnizleme(plan, false, neden, null, null, null, null, 0, 0, 0, null, null);
    }

    /** Donem ortasinda kayit yuzunden ucret orantilandi mi? (arayuzde aciklama satiri) */
    public boolean orantilandi() {
        return tamUcret != null && ucret != null && ucret.compareTo(tamUcret) != 0;
    }
}
