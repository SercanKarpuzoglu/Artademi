package com.artademi.teacher;

/**
 * Ogretmenin hakedis (kazanc) hesaplama tipi.
 *
 * <ul>
 *   <li>{@link #SAATLIK} — saat basina sabit ucret ({@code saatlikUcret} zorunlu, &gt; 0).</li>
 *   <li>{@link #CIRO_ORANI} — uretilen cironun yuzdesi ({@code ciroOrani} zorunlu, 0 &lt; oran &le; 100).</li>
 *   <li>{@link #OZEL_DERS} — ders basina sabit ucret ({@code dersBasiUcret} zorunlu, &gt; 0).</li>
 * </ul>
 *
 * <p>Model C: hakedis tipi GRUBA baglidir; her grup tek bir tip ile odenir. Ogretmen birden cok
 * tip TANIMLAYABILIR (oranlari tasir) — {@link TeacherHakedis} satirlari; grup hangisinin
 * uygulanacagini belirler. Bu kosullu zorunluluk {@code @HakedisTutarli} sinif duzeyi validasyonu
 * ile saglanir.
 */
public enum HakedisTipi {
    SAATLIK,
    CIRO_ORANI,
    OZEL_DERS
}
