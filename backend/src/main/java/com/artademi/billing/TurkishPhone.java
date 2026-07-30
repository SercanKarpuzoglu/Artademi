package com.artademi.billing;

import com.artademi.common.exception.ValidationException;

/**
 * Turk cep telefonu normallestirme — iyzico {@code gsmNumber} icin.
 *
 * <p>⚠️ Canli sandbox olcumü (2026-07-30): iyzico YALNIZCA {@code +90XXXXXXXXXX} bicimini kabul
 * eder; {@code 0555…}, {@code 555…}, {@code 90555…} hepsi HTTP 422 "Geçersiz telefon numarası"
 * doner. Kullanicilar dogal olarak {@code 0555 111 22 33} yazdigi icin donusumu BURADA yapariz —
 * form kullaniciyi bicim ezberlemeye zorlamaz.
 */
public final class TurkishPhone {

    private TurkishPhone() {
    }

    /**
     * Serbest girilmis numarayi {@code +90XXXXXXXXXX} bicimine cevirir.
     *
     * @throws ValidationException numara Turk cep numarasina benzemiyorsa (alan bazli hata mesaji)
     */
    public static String toE164(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Telefon numarası zorunludur");
        }
        String digits = raw.replaceAll("[^0-9]", "");

        // Ulke kodu varyantlarini soyup 10 haneli abone numarasina indir.
        if (digits.length() == 12 && digits.startsWith("90")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        // Turk cep numarasi: 10 hane ve 5 ile baslar (operator kodu).
        if (digits.length() != 10 || digits.charAt(0) != '5') {
            throw new ValidationException(
                    "Geçerli bir cep telefonu girin (örn. 0555 111 22 33)");
        }
        return "+90" + digits;
    }
}
