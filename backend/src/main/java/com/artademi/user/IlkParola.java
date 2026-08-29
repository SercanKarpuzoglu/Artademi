package com.artademi.user;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KULLANICI BASINA rastgele ilk parola ureticisi.
 *
 * <p><b>Neden var:</b> onceden her yeni kullanici AYNI sabit parolayla ({@code Artademi2026!})
 * aciliyordu. Bu parolayi bilen herkes, acilmis ama henuz ilk girisini yapmamis HERHANGI bir
 * hesaba girebilirdi — kullanici adlari tahmin edilebilir oldugu icin pratikte istismar edilebilir
 * bir acikti. Artik her hesap kendi parolasiyla acilir; hicbir parola tekrar etmez.
 *
 * <p><b>Politika garantisi:</b> duz rastgele cekim, "en az bir rakam/buyuk/kucuk/ozel karakter"
 * kuralini GARANTI ETMEZ — Otovers'ta uretimlerin %29'u realm politikasini ihlal ediyordu.
 * Bu yuzden her sinifdan en az bir karakter ONCE zorunlu olarak konur, kalan uzunluk rastgele
 * doldurulur ve sonuc karistirilir.
 */
public final class IlkParola {

    private static final SecureRandom RASTGELE = new SecureRandom();

    private static final String KUCUK = "abcdefghijkmnopqrstuvwxyz";   // l yok (1 ile karisir)
    private static final String BUYUK = "ABCDEFGHJKLMNPQRSTUVWXYZ";    // I, O yok
    private static final String RAKAM = "23456789";                    // 0, 1 yok
    private static final String OZEL = "!?*-+";
    private static final String HEPSI = KUCUK + BUYUK + RAKAM + OZEL;

    /** Uzunluk: kisa parola tahmin edilebilir, cok uzun elle aktarilamaz. */
    private static final int UZUNLUK = 14;

    private IlkParola() {
    }

    /**
     * Politikaya uygun, kriptografik olarak guvenli rastgele parola uretir.
     * Karistirilabilir karakterler (0/O, 1/l/I) DISLANIR — parola elle aktarilabilmeli.
     */
    public static String uret() {
        List<Character> karakterler = new ArrayList<>(UZUNLUK);
        // Once her siniftan birer tane: politika ihlali IMKANSIZ olsun.
        karakterler.add(rastgeleKarakter(KUCUK));
        karakterler.add(rastgeleKarakter(BUYUK));
        karakterler.add(rastgeleKarakter(RAKAM));
        karakterler.add(rastgeleKarakter(OZEL));
        while (karakterler.size() < UZUNLUK) {
            karakterler.add(rastgeleKarakter(HEPSI));
        }
        // Zorunlu karakterler hep bastaki sabit siralarda kalmasin.
        Collections.shuffle(karakterler, RASTGELE);

        StringBuilder sb = new StringBuilder(UZUNLUK);
        karakterler.forEach(sb::append);
        return sb.toString();
    }

    private static char rastgeleKarakter(String kaynak) {
        return kaynak.charAt(RASTGELE.nextInt(kaynak.length()));
    }
}
