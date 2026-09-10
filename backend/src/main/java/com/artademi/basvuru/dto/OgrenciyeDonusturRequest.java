package com.artademi.basvuru.dto;

import com.artademi.student.dto.VeliBilgisi;
import com.artademi.student.dto.VeliRequired;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Basvuruyu ogrenci kaydina donusturme.
 *
 * <p><b>Neden burada ek alan isteniyor?</b> On kayit formu bilincli olarak az veri toplar
 * (ilgiyi yakalamak icindir). Ama {@code students} tablosunda TC kimlik ve dogum tarihi
 * NOT NULL'dur; ayrica ogrenci yetiskin degilse anne VEYA baba icin ad+TC zorunludur.
 * Bu alanlar, talep gercekten ogrenciye donusurken alinir — ilgilenilmemis bir talepten
 * pesinen kisisel veri toplanmaz.
 *
 * <p>{@link VeliRequired} ile ogrenci formundaki AYNI kural uygulanir; ayri bir dogrulama
 * yazilmaz ki iki yer birbirinden ayrisip tutarsizlasmasin.
 */
@VeliRequired
public record OgrenciyeDonusturRequest(
        @NotBlank(message = "TC kimlik numarası zorunludur")
        @Pattern(regexp = "\\d{11}", message = "11 haneli TC kimlik numarası girin")
        String tcKimlikNo,
        @NotNull(message = "Doğum tarihi zorunludur") LocalDate dogumTarihi,
        boolean yetiskinMi,
        String anneAd,
        String anneTcKimlikNo,
        String anneTelefon,
        String babaAd,
        String babaTcKimlikNo,
        String babaTelefon,
        String evAdresi,
        /** Ayni TC kara listedeyse "yine de dönüştür" onayi (409 KARA_LISTE sonrasi). */
        Boolean karaListeOnayi) implements VeliBilgisi {
}
