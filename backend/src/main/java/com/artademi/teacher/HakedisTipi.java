package com.artademi.teacher;

/**
 * Ogretmenin hakedis (kazanc) hesaplama tipi.
 *
 * <ul>
 *   <li>{@link #SAATLIK} — saat basina sabit ucret ({@code saatlikUcret} zorunlu, &gt; 0).</li>
 *   <li>{@link #CIRO_ORANI} — uretilen cironun yuzdesi ({@code ciroOrani} zorunlu, 0 &lt; oran &le; 100).</li>
 * </ul>
 *
 * Bu kosullu zorunluluk {@code @HakedisTutarli} sinif duzeyi validasyonu ile saglanir.
 */
public enum HakedisTipi {
    SAATLIK,
    CIRO_ORANI
}
