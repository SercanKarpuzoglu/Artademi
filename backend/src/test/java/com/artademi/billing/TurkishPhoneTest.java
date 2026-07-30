package com.artademi.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.artademi.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Telefon normallestirme — iyzico yalnizca +90XXXXXXXXXX kabul ediyor (canli 422 ile dogrulandi).
 */
class TurkishPhoneTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "05551112233",        // en yaygin kullanici girisi
            "0555 111 22 33",     // bosluklu
            "0555-111-22-33",     // tireli
            "(0555) 111 22 33",   // parantezli
            "5551112233",         // basta sifir yok
            "905551112233",       // ulke kodu, + yok
            "+905551112233",      // zaten dogru
            "+90 555 111 22 33",  // dogru ama bosluklu
    })
    void cesitliGirisler_ayniE164(String giris) {
        assertThat(TurkishPhone.toE164(giris)).isEqualTo("+905551112233");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567890",   // 5 ile baslamiyor (cep degil)
            "0212 555 44 33", // sabit hat
            "555111223",    // eksik hane
            "05551112233123", // fazla hane
            "abc",
    })
    void gecersizGirisler_alanHatasi(String giris) {
        assertThatThrownBy(() -> TurkishPhone.toE164(giris))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cep telefonu");
    }

    @Test
    void bosVeyaNull_zorunluHatasi() {
        assertThatThrownBy(() -> TurkishPhone.toE164(null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> TurkishPhone.toE164("   "))
                .isInstanceOf(ValidationException.class);
    }
}
