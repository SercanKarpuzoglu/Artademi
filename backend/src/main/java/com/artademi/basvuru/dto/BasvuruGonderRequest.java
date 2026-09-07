package com.artademi.basvuru.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public on kayit formu gonderimi — KIMLIKSIZ uc.
 *
 * <p>Bilincli olarak AZ alan istenir: on kayit formunun isi ilgiyi yakalamaktir, ogrenci
 * kaydini tamamlamak degil. TC kimlik ve dogum tarihi burada SORULMAZ (hem surtunme yaratir
 * hem de gereksiz kisisel veri toplamak olur); bunlar ogrenciye donusturme adiminda alinir.
 *
 * @param website honeypot — gorunmez alan; doluysa gonderim bot kabul edilir
 */
public record BasvuruGonderRequest(
        @NotBlank(message = "Ad zorunludur") @Size(max = 100) String ad,
        @NotBlank(message = "Soyad zorunludur") @Size(max = 100) String soyad,
        @NotBlank(message = "Telefon zorunludur") @Size(max = 30) String telefon,
        @Email(message = "Geçerli bir e-posta girin") @Size(max = 255) String email,
        @Size(max = 200) String veliAdi,
        Long bransId,
        @Size(max = 1000, message = "Mesaj en fazla 1000 karakter olabilir") String mesaj,
        String website) {

    /** Honeypot dolu ise bot: insan bu alani goremez. */
    public boolean botMu() {
        return website != null && !website.isBlank();
    }
}
