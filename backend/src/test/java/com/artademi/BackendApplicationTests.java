package com.artademi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Iskelet asamasinda DB/context gerektirmeyen basit bir akil-saglik testi.
 * Spring context + Testcontainers tabanli gercek testler sonraki parcalarda gelecek.
 */
class BackendApplicationTests {

    @Test
    void sanity() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
