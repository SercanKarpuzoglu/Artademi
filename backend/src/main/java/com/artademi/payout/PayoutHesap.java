package com.artademi.payout;

import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;
import java.math.BigDecimal;

/**
 * Hesaplama sonucu tasiyan dahili (servis ici) deger nesnesi. Hem KAYDEDILECEK hakediş (hesapla) hem
 * de KAYITSIZ onizleme (onizle) ayni hesaplama sonucundan uretildigi icin ortak tasiyici budur;
 * PayoutResponse buradan veya kaydedilmis Payout'tan kurulabilir.
 *
 * <p>Tipe gore dolu alanlar:
 * <ul>
 *   <li>SAATLIK: {@code dersSayisi} + {@code birimUcret} (toplam/kdv/netCiro/oran null).</li>
 *   <li>CIRO_ORANI: {@code toplamTahsilat} + {@code kdvOrani} + {@code netCiro} + {@code oran}
 *       (dersSayisi/birimUcret null).</li>
 * </ul>
 *
 * <p>PARA KURALI: tum tutarlar {@link BigDecimal} scale 2 / oranlar scale 2, HALF_UP. Double/float YOK.
 */
public record PayoutHesap(
        Teacher ogretmen,
        String donem,
        HakedisTipi hakedisTipi,
        BigDecimal hesaplananTutar,
        Integer dersSayisi,
        BigDecimal birimUcret,
        BigDecimal toplamTahsilat,
        BigDecimal kdvOrani,
        BigDecimal netCiro,
        BigDecimal oran) {
}
