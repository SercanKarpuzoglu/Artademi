package com.artademi.finance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Otomatik aylik tahakkuk uretiminin (veya onizlemesinin) ozet sonucu.
 *
 * <ul>
 *   <li>{@code uretilenSayisi} — yeni olusturulan (onizlemede: olusturulacak) tahakkuk sayisi.</li>
 *   <li>{@code atlananSayisi} — ayni donemde zaten tahakkugu olan (idempotent atlama) kayit sayisi.</li>
 *   <li>{@code toplamTutar} — uretilen (onizlemede: uretilecek) tahakkuklarin toplam tutari,
 *       {@link BigDecimal} scale 2 HALF_UP.</li>
 *   <li>{@code ozet} — uretilen (onizlemede: uretilecek) kalemlerin listesi.</li>
 * </ul>
 */
public record AccrualGenerationResult(
        String donem,
        int uretilenSayisi,
        int atlananSayisi,
        BigDecimal toplamTutar,
        List<OzetKalemi> ozet) {

    /** Uretilen/uretilecek tek bir tahakkuk kalemi (ogrenci+grup+tutar). */
    public record OzetKalemi(
            Long ogrenciId,
            Long grupId,
            BigDecimal tutar) {
    }
}
