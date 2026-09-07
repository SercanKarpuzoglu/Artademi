package com.artademi.gizli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Sifreleme cekirdegi.
 *
 * <p>Bu sinifta test edilen sey "calisiyor mu" degil, <b>guvenlik ozelliklerinin gercekten
 * saglandigi</b>: kurcalanan veri fark ediliyor mu, ayni parola iki kurumda ayni sifreli
 * metni uretiyor mu, anahtar yokken sessizce duz metin yaziliyor mu. Bunlarin hepsi
 * sessiz basarisizliklardir — kod "calisir" gorunur, koruma yoktur.
 */
class SifrelemeTest {

    /** Test icin sabit 32 baytlik anahtar (uretimde openssl rand -base64 32). */
    private static final String ANAHTAR =
            Base64.getEncoder().encodeToString(new byte[32]);

    private final Sifreleme s = new Sifreleme(ANAHTAR);

    @Test
    void sifrele_coz_gidisDonus() {
        String duz = "netgsm-parolam-123";
        assertThat(s.coz(s.sifrele(duz))).isEqualTo(duz);
    }

    @Test
    void turkceKarakterKorunur() {
        // Kimlik bilgileri Turkce karakter icerebilir; UTF-8 zinciri bozulmamali.
        String duz = "Şifre-ĞÜİÖÇ-şğüıöç";
        assertThat(s.coz(s.sifrele(duz))).isEqualTo(duz);
    }

    @Test
    void ayniDuzMetin_FARKLI_sifreliMetinUretir() {
        // ⚠️ En kritik ozellik: sabit IV kullanilsaydi, iki kurumun AYNI parolayi
        // kullandigi veritabanina bakmakla anlasilirdi.
        String duz = "ayni-parola";
        assertThat(s.sifrele(duz)).isNotEqualTo(s.sifrele(duz));
    }

    @Test
    void kurcalananDeger_COZULMEZ_sessizceGecmez() {
        // GCM kimlik dogrulamali: tek bir bayt degisse cozme PATLAR. CBC olsaydi
        // coplukle cozulur, biz de onu saglayiciya "kullanici adi" diye gonderirdik.
        String sifreli = s.sifrele("gizli-deger");
        String[] p = sifreli.split(":", 3);
        byte[] bozuk = Base64.getDecoder().decode(p[2]);
        bozuk[0] ^= 0x01;
        String kurcalanmis = p[0] + ":" + p[1] + ":" + Base64.getEncoder().encodeToString(bozuk);

        assertThatThrownBy(() -> s.coz(kurcalanmis))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("çözülemedi");
    }

    @Test
    void baskaAnahtarlaCozulemez() {
        String sifreli = s.sifrele("gizli");
        byte[] farkli = new byte[32];
        farkli[0] = 7;
        Sifreleme digeri = new Sifreleme(Base64.getEncoder().encodeToString(farkli));

        assertThatThrownBy(() -> digeri.coz(sifreli)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void surumOnekiVar_rotasyonIcin() {
        // Onek olmadan ileride anahtar rotasyonu tum tabloyu tek seferde donusturmeyi gerektirir.
        assertThat(s.sifrele("x")).startsWith("v1:");
    }

    @Test
    void taninmayanBicim_reddedilir() {
        assertThatThrownBy(() -> s.coz("duz-metin-olarak-yazilmis"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("biçimi tanınmıyor");
    }

    // ---------- fail-closed ----------

    @Test
    void anahtarYOK_sifrelemeREDDEDILIR() {
        // ⚠️ "Anahtar yoksa duz metin yaz" gibi bir kolaylik, korumayi SESSIZCE yok ederdi.
        Sifreleme anahtarsiz = new Sifreleme("");

        assertThat(anahtarsiz.yapilandirilmisMi()).isFalse();
        assertThatThrownBy(() -> anahtarsiz.sifrele("gizli"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARTADEMI_SIFRELEME_ANAHTARI");
    }

    @Test
    void yanlisUzunluktaAnahtar_ACILISTA_patlar() {
        // Kisa anahtarla sessizce devam etmek zayif sifreleme demektir.
        String kisa = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new Sifreleme(kisa))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bayt");
    }

    @Test
    void base64OlmayanAnahtar_ACILISTA_patlar() {
        assertThatThrownBy(() -> new Sifreleme("bu-base64-degil-!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    // ---------- maskeleme ----------

    @Test
    void maske_son4HaneGosterir() {
        assertThat(Sifreleme.maskele("abcdef1234")).isEqualTo("••••1234");
    }

    @Test
    void maske_kisaDeger_TAMAMEN_maskelenir() {
        // 4 karakterlik bir degerin "son 4 hanesi" degerin KENDISIDIR.
        assertThat(Sifreleme.maskele("1234")).isEqualTo("••••");
        assertThat(Sifreleme.maskele("ab")).isEqualTo("••••");
    }

    @Test
    void maske_bosDeger_null() {
        assertThat(Sifreleme.maskele(null)).isNull();
        assertThat(Sifreleme.maskele("  ")).isNull();
    }
}
