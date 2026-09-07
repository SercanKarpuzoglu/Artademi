package com.artademi.gizli;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Kurum gizli ayarlarinin sifrelenmesi (AES-256-GCM).
 *
 * <h2>Neden GCM</h2>
 * GCM <b>kimlik dogrulamali</b> sifrelemedir: veritabanindaki deger kurcalanirsa cozme
 * ISLEMI PATLAR. CBC gibi bir mod kullansaydik kurcalanmis veri sessizce coplukle cozulur,
 * biz de onu saglayiciya "kullanici adi" diye gonderirdik.
 *
 * <h2>Her sifrelemede YENI IV</h2>
 * Ayni duz metin her seferinde FARKLI sifreli metin uretir. Sabit IV kullanilsaydi, iki
 * kurumun ayni parolayi kullandigi veritabanina bakmakla anlasilirdi.
 *
 * <h2>Surum oneki</h2>
 * Saklanan bicim {@code "v1:<base64 IV>:<base64 sifreli>"}. Onek BILINCLIDIR: anahtar
 * rotasyonu gerektiginde eski kayitlar v1 anahtariyla okunmaya devam eder, yeniler v2
 * yazilir. Onek olmadan rotasyon icin tum tabloyu tek seferde donusturmek gerekirdi.
 *
 * <h2>⚠️ Fail-closed</h2>
 * Anahtar yapilandirilmamissa sifreleme de cozme de HATA verir. "Anahtar yoksa duz metin
 * yaz" gibi bir kolaylik, tam da korumaya calistigimiz seyi yok ederdi — ve sessizce.
 */
@Component
public class Sifreleme {

    /** Saklanan bicimin surum oneki. Rotasyonda v2 eklenir, v1 okunmaya devam eder. */
    static final String SURUM = "v1";

    private static final String DONUSUM = "AES/GCM/NoPadding";
    /** GCM icin onerilen IV uzunlugu (96 bit). */
    private static final int IV_UZUNLUK = 12;
    /** Kimlik dogrulama etiketi uzunlugu (bit). */
    private static final int ETIKET_BIT = 128;
    /** AES-256 icin anahtar uzunlugu (bayt). */
    private static final int ANAHTAR_BAYT = 32;

    private static final SecureRandom RASTGELE = new SecureRandom();

    private final SecretKeySpec anahtar;

    /**
     * @param base64Anahtar {@code ARTADEMI_SIFRELEME_ANAHTARI} — base64 kodlu 32 baytlik
     *     anahtar. Uretmek icin: {@code openssl rand -base64 32}. Bos birakilabilir
     *     (uygulama ayaga kalkar) ama o zaman gizli ayar YAZILAMAZ/OKUNAMAZ.
     */
    public Sifreleme(
            @Value("${artademi.sifreleme.anahtar:}") String base64Anahtar) {
        this.anahtar = anahtarCoz(base64Anahtar);
    }

    /** Anahtar yapilandirilmis mi? Uc noktasi buna bakip anlasilir hata dondurebilir. */
    public boolean yapilandirilmisMi() {
        return anahtar != null;
    }

    /**
     * Duz metni sifreler.
     *
     * @return {@code "v1:<base64 IV>:<base64 sifreli>"}
     * @throws IllegalStateException anahtar yapilandirilmamissa (fail-closed)
     */
    public String sifrele(String duzMetin) {
        anahtarZorunlu();
        try {
            byte[] iv = new byte[IV_UZUNLUK];
            RASTGELE.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(DONUSUM);
            cipher.init(Cipher.ENCRYPT_MODE, anahtar, new GCMParameterSpec(ETIKET_BIT, iv));
            byte[] sifreli = cipher.doFinal(duzMetin.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder e = Base64.getEncoder();
            return SURUM + ":" + e.encodeToString(iv) + ":" + e.encodeToString(sifreli);
        } catch (Exception e) {
            // ⚠️ Duz metin HICBIR ZAMAN mesaja veya loga girmez; sebebi de tasimayiz
            // (istisna zinciri bazi kutuphanelerde girdi parcasi icerebiliyor).
            throw new IllegalStateException("Değer şifrelenemedi");
        }
    }

    /**
     * Sifreli degeri cozer.
     *
     * @throws IllegalStateException anahtar yoksa, bicim taninmiyorsa VEYA deger
     *     kurcalanmissa (GCM etiketi tutmaz)
     */
    public String coz(String saklanan) {
        anahtarZorunlu();
        String[] parca = saklanan == null ? new String[0] : saklanan.split(":", 3);
        if (parca.length != 3 || !SURUM.equals(parca[0])) {
            throw new IllegalStateException("Şifreli değer biçimi tanınmıyor");
        }
        try {
            Base64.Decoder d = Base64.getDecoder();
            byte[] iv = d.decode(parca[1]);
            byte[] sifreli = d.decode(parca[2]);

            Cipher cipher = Cipher.getInstance(DONUSUM);
            cipher.init(Cipher.DECRYPT_MODE, anahtar, new GCMParameterSpec(ETIKET_BIT, iv));
            return new String(cipher.doFinal(sifreli), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Kurcalanmis veri buraya duser (GCM etiketi tutmaz) — sessizce coplukle
            // devam ETMEYIZ.
            throw new IllegalStateException("Şifreli değer çözülemedi (bozuk veya kurcalanmış)");
        }
    }

    /**
     * Maskeli gosterim: son 4 karakter, oncesi noktali.
     *
     * <p>Yonetici "dogru anahtari mi girdim" diye bakabilsin diye bu kadari saklanir.
     * 4 karakterden kisa degerler TAMAMEN maskelenir — kisa bir degerin son 4 hanesi
     * degerin kendisi olurdu.
     */
    public static String maskele(String duzMetin) {
        if (duzMetin == null || duzMetin.isBlank()) {
            return null;
        }
        String t = duzMetin.trim();
        if (t.length() <= 4) {
            return "••••";
        }
        return "••••" + t.substring(t.length() - 4);
    }

    private void anahtarZorunlu() {
        if (anahtar == null) {
            throw new IllegalStateException(
                    "Şifreleme anahtarı yapılandırılmamış (ARTADEMI_SIFRELEME_ANAHTARI). "
                            + "Gizli ayarlar bu anahtar olmadan saklanamaz.");
        }
    }

    private static SecretKeySpec anahtarCoz(String base64Anahtar) {
        if (base64Anahtar == null || base64Anahtar.isBlank()) {
            return null;
        }
        byte[] ham;
        try {
            ham = Base64.getDecoder().decode(base64Anahtar.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ARTADEMI_SIFRELEME_ANAHTARI base64 değil (openssl rand -base64 32)");
        }
        if (ham.length != ANAHTAR_BAYT) {
            // Yanlis uzunlukta anahtarla SESSIZCE devam etmek, zayif sifreleme demektir.
            throw new IllegalStateException(
                    "ARTADEMI_SIFRELEME_ANAHTARI 32 bayt olmalı (şu an " + ham.length + ")");
        }
        return new SecretKeySpec(ham, "AES");
    }

}
