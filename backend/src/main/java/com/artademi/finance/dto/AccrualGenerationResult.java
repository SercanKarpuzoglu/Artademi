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
 *   <li>{@code atlananDenemeOgrenciler} — aidatli gruba AKTIF kayitli ama statusu DENEME oldugu icin
 *       tahakkuk URETILMEYEN ogrenciler. Uyari amacli: kurum deneme dersinden sonra ogrenciyi elle
 *       AKTIF yapmayi unutmussa burada gorur. {@code atlananSayisi}'na DAHIL DEGILDIR.</li>
 * </ul>
 */
public record AccrualGenerationResult(
        String donem,
        int uretilenSayisi,
        int atlananSayisi,
        BigDecimal toplamTutar,
        List<OzetKalemi> ozet,
        List<DenemeOgrenci> atlananDenemeOgrenciler) {

    /** Uretilen/uretilecek tek bir tahakkuk kalemi (ogrenci+grup+tutar). */
    public record OzetKalemi(
            Long ogrenciId,
            Long grupId,
            /** NET tutar (indirim dusulmus). */
            BigDecimal tutar,
            BigDecimal brut,
            BigDecimal indirim,
            String indirimAciklama) {
    }

    /** DENEME statusu yuzunden atlanan ogrenci (uyari satiri). */
    public record DenemeOgrenci(
            Long ogrenciId,
            String ad,
            String soyad,
            Long grupId,
            String grupAd) {
    }
}
