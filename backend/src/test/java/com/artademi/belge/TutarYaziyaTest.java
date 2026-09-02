package com.artademi.belge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tutar-yaziya cevrimi. Turkce'ye ozgu "bir bin"/"bir yuz" kurallari burada kilitlenir —
 * bu iki kural atlandiginda makbuzda "bir bin bes yuz TL" gibi YANLIS bir ifade olusur ve
 * hata sessizdir (kimse patlamaz, sadece belge yanlis basilir).
 */
class TutarYaziyaTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "0, sıfır",
        "1, bir",
        "9, dokuz",
        "10, on",
        "11, on bir",
        "90, doksan",
        // "bir yuz" DENMEZ
        "100, yüz",
        "101, yüz bir",
        "200, iki yüz",
        "999, dokuz yüz doksan dokuz",
        // "bir bin" DENMEZ
        "1000, bin",
        "1001, bin bir",
        "1500, bin beş yüz",
        "2000, iki bin",
        "21000, yirmi bir bin",
        "100000, yüz bin",
        // milyonda "bir" KORUNUR (bin'den farkli)
        "1000000, bir milyon",
        "2500000, iki milyon beş yüz bin",
    })
    void cevir_turkceKurallar(long sayi, String beklenen) {
        assertThat(TutarYaziya.cevir(sayi)).isEqualTo(beklenen);
    }

    @Test
    void makbuzSatiri_kurusluTutar() {
        assertThat(TutarYaziya.makbuzSatiri(new BigDecimal("2500.50")))
                .isEqualTo("İKİ BİN BEŞ YÜZ TL ELLİ KR");
    }

    @Test
    void makbuzSatiri_kurusYoksaSifirYazilir() {
        // Bos birakmak makbuzda eksik/tahrifat izlenimi verir; "SIFIR KR" yazilir.
        assertThat(TutarYaziya.makbuzSatiri(new BigDecimal("2000.00")))
                .isEqualTo("İKİ BİN TL SIFIR KR");
    }

    @Test
    void makbuzSatiri_kurusYuvarlanir_rakamlaYaziTutmali() {
        // 0.005 -> 0.01 (HALF_UP). Makbuzdaki rakam da ayni sekilde yuvarlanacagi icin
        // ikisi birbirini tutar.
        assertThat(TutarYaziya.makbuzSatiri(new BigDecimal("10.005")))
                .isEqualTo("ON TL BİR KR");
    }

    @Test
    void makbuzSatiri_buyukTutar() {
        assertThat(TutarYaziya.makbuzSatiri(new BigDecimal("1234567.89")))
                .isEqualTo("BİR MİLYON İKİ YÜZ OTUZ DÖRT BİN BEŞ YÜZ ALTMIŞ YEDİ TL SEKSEN DOKUZ KR");
    }
}
