package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import java.math.BigDecimal;

/**
 * Tek bir hakedis tipi satiri (Model C — ogretmen birden cok TANIMLAYABILIR). Yalnizca {@code tip}
 * ile eslesen tutar alani anlamlidir; digerleri null beklenir. Tutar zorunlulugu/gecerliligi
 * {@link HakedisTutarli} (liste duzeyi) validasyonu ile saglanir:
 * <ul>
 *   <li>SAATLIK -> {@code saatlikUcret} (&gt; 0).</li>
 *   <li>CIRO_ORANI -> {@code ciroOrani} (0 &lt; oran &le; 100).</li>
 *   <li>OZEL_DERS -> {@code dersBasiUcret} (&gt; 0).</li>
 * </ul>
 */
public record HakedisSatiriRequest(
        HakedisTipi tip,
        BigDecimal saatlikUcret,
        BigDecimal ciroOrani,
        BigDecimal dersBasiUcret) {
}
