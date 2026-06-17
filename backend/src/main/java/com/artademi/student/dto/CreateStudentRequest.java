package com.artademi.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Ogrenci olusturma istegi. Zorunlu alanlar Bean Validation ile dogrulanir
 * (eksikse 400 VALIDATION_ERROR). Veli zorunlulugu (yetiskin degilse en az bir veli)
 * is kurali olarak StudentService'te enforce edilir.
 *
 * <p>tenant_id ve status ALINMAZ: tenant JWT'den gelir, yeni kayit her zaman DENEME.
 */
@VeliRequired
public record CreateStudentRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotBlank(message = "Soyad zorunludur")
        String soyad,

        @NotBlank(message = "TC kimlik no zorunludur")
        @Pattern(regexp = "\\d{11}", message = "TC kimlik no 11 haneli olmalıdır")
        String tcKimlikNo,

        @NotNull(message = "Doğum tarihi zorunludur")
        LocalDate dogumTarihi,

        String telefon,
        boolean yetiskinMi,

        // Veli bilgisi (opsiyonel; is kurali serviste). TC girilmisse 11 hane olmali.
        String anneAd,
        @Pattern(regexp = "\\d{11}", message = "Anne TC kimlik no 11 haneli olmalıdır")
        String anneTcKimlikNo,
        String anneTelefon,
        String babaAd,
        @Pattern(regexp = "\\d{11}", message = "Baba TC kimlik no 11 haneli olmalıdır")
        String babaTcKimlikNo,
        String babaTelefon,
        String veliMeslek,
        String evAdresi,
        String veliMail) implements VeliBilgisi {
}
