package com.artademi.report.dto;

import com.artademi.payout.PayoutDurumu;
import com.artademi.teacher.HakedisTipi;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ogretmen hakedisleri raporu (RAPOR — salt okunur). Hicbir tenant_id alani SIZMAZ.
 *
 * <p>Tum parasal alanlar {@link BigDecimal}, scale 2 (HALF_UP). {@code toplamHakedis} kalemlerin
 * toplamidir.
 *
 * @param donem        "YYYY-MM" donem
 * @param toplamHakedis donemdeki tum hakedislerin toplami
 * @param kalemler     hakedis kalemleri
 */
public record TeacherPayoutsResponse(
        String donem,
        BigDecimal toplamHakedis,
        List<TeacherPayoutRow> kalemler) {

    /**
     * Tek hakedis kalemi.
     *
     * @param ogretmenId      ogretmen id
     * @param ad              ogretmen adi
     * @param soyad           ogretmen soyadi
     * @param hakedisTipi     hakedis tipi (SAATLIK / CIRO_ORANI)
     * @param hesaplananTutar hesaplanan tutar
     * @param durum           hakedis durumu (HESAPLANDI / ODENDI)
     */
    public record TeacherPayoutRow(
            Long ogretmenId,
            String ad,
            String soyad,
            HakedisTipi hakedisTipi,
            BigDecimal hesaplananTutar,
            PayoutDurumu durum) {
    }
}
