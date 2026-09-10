package com.artademi.silme;

import com.artademi.common.exception.NotFoundException;

/**
 * Yumusak silinebilen kayit turleri ve URL adlari ({@code /api/silme/{tur}/{id}}). Her tur icin tablo adi ve
 * "silinenler" listesinde gosterilecek ad ifadesi (native SQL) burada; @SQLRestriction'i asan tek yer bu.
 */
public enum SilinebilirTur {
    OGRENCI("ogrenci", "students", "ad || ' ' || soyad", "Öğrenci"),
    GRUP("grup", "lesson_group", "ad", "Grup"),
    EGITMEN("egitmen", "teachers", "ad || ' ' || soyad", "Eğitmen"),
    SALON("salon", "rooms", "ad", "Salon"),
    BRANS("brans", "branches", "ad", "Branş"),
    SUBE("sube", "sube", "ad", "Şube"),
    URUN("urun", "product", "ad", "Ürün"),
    TEDARIKCI("tedarikci", "tedarikci", "ad", "Tedarikçi"),
    KASA("kasa", "kasa", "ad", "Kasa"),
    TAHAKKUK("tahakkuk", "accrual", "'Tahakkuk ' || donem || ' · ' || tutar || ' ₺'", "Tahakkuk"),
    ODEME("odeme", "payment", "'Ödeme ' || odeme_tarihi || ' · ' || tutar || ' ₺'", "Ödeme"),
    GIDER("gider", "expense", "'Gider ' || gider_tarihi || ' · ' || tutar || ' ₺'", "Gider"),
    SATIS("satis", "sale", "'Satış ' || satis_tarihi || ' · ' || toplam_tutar || ' ₺'", "Satış"),
    PAKET("paket", "ders_paketi", "ad", "Ders paketi"),
    TELAFI("telafi", "telafi_hakki", "'Telafi hakkı ' || verilme_tarihi", "Telafi hakkı"),
    BASVURU("basvuru", "basvuru", "ad || ' ' || soyad", "Başvuru"),
    DERS_SAATI("ders-saati", "schedule", "gun || ' ' || baslangic_saati", "Ders saati"),
    YOKLAMA_OTURUMU("yoklama-oturumu", "attendance_session", "'Yoklama ' || tarih", "Yoklama oturumu"),
    INDIRIM("indirim", "indirim_tanimi", "ad", "İndirim");

    private final String yol;
    private final String tablo;
    private final String adIfadesi;
    private final String etiket;

    SilinebilirTur(String yol, String tablo, String adIfadesi, String etiket) {
        this.yol = yol;
        this.tablo = tablo;
        this.adIfadesi = adIfadesi;
        this.etiket = etiket;
    }

    public String yol() {
        return yol;
    }

    public String tablo() {
        return tablo;
    }

    public String adIfadesi() {
        return adIfadesi;
    }

    public String etiket() {
        return etiket;
    }

    /** URL parcasindan tur; bilinmeyen -> 404 (kullaniciya "yok" demek, 400'den daha dogru). */
    public static SilinebilirTur fromYol(String yol) {
        for (SilinebilirTur t : values()) {
            if (t.yol.equals(yol)) {
                return t;
            }
        }
        throw new NotFoundException("Bilinmeyen kayıt türü: " + yol);
    }
}
