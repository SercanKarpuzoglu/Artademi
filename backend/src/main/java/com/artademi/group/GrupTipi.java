package com.artademi.group;

/**
 * Grubun tipi; koşullu ücret/salon zorunluluğunu belirler.
 *
 * <ul>
 *   <li>{@link #GRUP} — toplu sınıf: {@code salon} ve {@code aylikAidat} (&gt; 0) zorunlu;
 *       {@code dersBasiUcret} yok sayılır.</li>
 *   <li>{@link #OZEL} — birebir özel ders: {@code dersBasiUcret} (&gt; 0) zorunlu; {@code salon}
 *       opsiyonel, {@code aylikAidat} yok sayılır.</li>
 * </ul>
 *
 * Bu koşullu zorunluluk {@code @GrupTutarli} sınıf düzeyi validasyonu ile sağlanır.
 */
public enum GrupTipi {
    GRUP,
    OZEL
}
