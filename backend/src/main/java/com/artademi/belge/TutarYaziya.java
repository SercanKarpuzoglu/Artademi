package com.artademi.belge;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tutari Turkce yaziya cevirir — makbuzlardaki "YALNIZ …" satiri icin.
 *
 * <p>Turkce'ye ozgu iki kural vardir ve ikisi de sik atlanir:
 * <ul>
 *   <li><b>"bir bin" DENMEZ</b>: 1.000 → "bin" (ama 21.000 → "yirmi bir bin").</li>
 *   <li><b>"bir yuz" DENMEZ</b>: 100 → "yuz" (ama 200 → "iki yuz").</li>
 * </ul>
 *
 * <p>Kurus 2 haneye YUVARLANIR (HALF_UP) — makbuzdaki rakam ile yazi birbirini tutmalidir.
 */
public final class TutarYaziya {

    private static final String[] BIRLER = {
        "", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz"
    };

    private static final String[] ONLAR = {
        "", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan"
    };

    /** Uclu gruplarin adlari; indis = grup sirasi (0 = birler grubu). */
    private static final String[] GRUPLAR = {"", "bin", "milyon", "milyar", "trilyon"};

    private TutarYaziya() {
    }

    /**
     * Makbuz satiri uretir: {@code "İKİ BİN BEŞ YÜZ TL ELLİ KR"}.
     *
     * <p>Kurus sifirsa "SIFIR KR" yazilir — bos birakmak makbuzda eksik/tahrifat izlenimi verir.
     */
    public static String makbuzSatiri(BigDecimal tutar) {
        BigDecimal yuvarlanmis = tutar.setScale(2, RoundingMode.HALF_UP);
        long lira = yuvarlanmis.longValue();
        int kurus = yuvarlanmis.remainder(BigDecimal.ONE)
                .movePointRight(2).abs().intValue();

        String liraYazi = cevir(lira);
        String kurusYazi = kurus == 0 ? "sıfır" : cevir(kurus);
        return (liraYazi + " TL " + kurusYazi + " KR").toUpperCase(new java.util.Locale("tr", "TR"));
    }

    /** Sayiyi Turkce yaziya cevirir (0 → "sıfır"). Negatif deger beklenmez. */
    public static String cevir(long sayi) {
        if (sayi == 0) {
            return "sıfır";
        }
        if (sayi < 0) {
            return "eksi " + cevir(-sayi);
        }

        StringBuilder sb = new StringBuilder();
        int grup = 0;
        long kalan = sayi;
        String[] parcalar = new String[GRUPLAR.length];

        while (kalan > 0 && grup < GRUPLAR.length) {
            int uclu = (int) (kalan % 1000);
            if (uclu > 0) {
                String metin = ucluCevir(uclu);
                // "bir bin" DENMEZ: 1.000 = "bin". Ama 1.000.000 = "bir milyon" (o dogru).
                if (grup == 1 && uclu == 1) {
                    metin = "";
                }
                parcalar[grup] = (metin.isEmpty() ? "" : metin + " ") + GRUPLAR[grup];
            }
            kalan /= 1000;
            grup++;
        }

        for (int i = parcalar.length - 1; i >= 0; i--) {
            if (parcalar[i] != null && !parcalar[i].isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(parcalar[i].trim());
            }
        }
        return sb.toString().trim();
    }

    /** 1-999 arasi bir sayiyi cevirir. */
    private static String ucluCevir(int n) {
        StringBuilder sb = new StringBuilder();
        int yuz = n / 100;
        int on = (n % 100) / 10;
        int bir = n % 10;

        if (yuz > 0) {
            // "bir yuz" DENMEZ: 100 = "yuz", 200 = "iki yuz".
            if (yuz > 1) {
                sb.append(BIRLER[yuz]).append(' ');
            }
            sb.append("yüz");
        }
        if (on > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(ONLAR[on]);
        }
        if (bir > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(BIRLER[bir]);
        }
        return sb.toString();
    }
}
