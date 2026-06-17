package com.artademi.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Ogrenci guncelleme istegi. Statu BURADAN degismez (ona ozel PATCH endpoint var).
 * Veli zorunlulugu (yetiskin degilse en az bir veli) {@link VeliRequired} ile enforce edilir.
 */
@VeliRequired
public record UpdateStudentRequest(
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
