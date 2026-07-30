package com.artademi.billing.dto;

import com.artademi.billing.TurkishPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Checkout baslatma istegi — iyzico'nun zorunlu tuttugu fatura bilgileri. Kart bilgisi BURADA
 * YOK; kart iyzico'nun barindirdigi formda girilir (PCI yuku saglayicida).
 */
public record CheckoutStartRequest(
        @NotBlank(message = "Ad zorunludur") String ad,
        @NotBlank(message = "Soyad zorunludur") String soyad,
        @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin")
        String email,
        @NotBlank(message = "Telefon zorunludur") String telefon,
        @NotBlank(message = "TC kimlik veya vergi numarası zorunludur")
        @Pattern(regexp = "\\d{10,11}", message = "10 haneli VKN veya 11 haneli TCKN girin")
        String kimlikVergiNo,
        @NotBlank(message = "Fatura adresi zorunludur") String adres,
        @NotBlank(message = "Şehir zorunludur") String sehir) {

    public CheckoutCustomer toCustomer() {
        // Telefon +90XXXXXXXXXX'e normallestirilir: iyzico baska hicbir bicimi kabul etmiyor.
        return new CheckoutCustomer(ad, soyad, email, TurkishPhone.toE164(telefon), kimlikVergiNo,
                adres, sehir, "Türkiye");
    }
}
