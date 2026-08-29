package com.artademi.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Ilk parola ureticisi: politika garantisi ve tekrarsizlik.
 *
 * <p>Onceki acik: TUM kullanicilar ayni sabit parolayla aciliyordu. Bu testler o durumun
 * geri gelmemesini ve uretilen parolanin realm politikasini HER SEFERINDE saglamasini korur.
 */
class IlkParolaTest {

    @RepeatedTest(200)
    void uretilenParola_politikayiHERZAMAN_saglar() {
        String p = IlkParola.uret();

        assertThat(p).hasSize(14);
        assertThat(p).matches(".*[a-z].*").as("kucuk harf");
        assertThat(p).matches(".*[A-Z].*").as("buyuk harf");
        assertThat(p).matches(".*[0-9].*").as("rakam");
        assertThat(p).matches(".*[!?*+-].*").as("ozel karakter");
    }

    @RepeatedTest(100)
    void karisabilenKarakterler_KULLANILMAZ() {
        // Parola elle aktarilabilmeli: 0/O ve 1/l/I ayirt edilemez, disarida birakildi.
        assertThat(IlkParola.uret()).doesNotContain("0").doesNotContain("O")
                .doesNotContain("1").doesNotContain("l").doesNotContain("I");
    }

    @Test
    void herCagri_FARKLI_parolaUretir() {
        Set<String> uretilenler = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            uretilenler.add(IlkParola.uret());
        }
        // Sabit parola donsaydi kume 1 elemanli olurdu — asil korunan davranis budur.
        assertThat(uretilenler).hasSize(500);
    }
}
