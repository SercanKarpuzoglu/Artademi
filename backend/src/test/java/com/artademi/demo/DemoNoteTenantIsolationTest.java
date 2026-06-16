package com.artademi.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tenant izolasyon kaniti: ayni endpoint, X-Tenant-Id header'ina (UUID) gore
 * yalnizca o tenant'in verisini dondurmeli; bir tenant'in verisi digerine SIZMAMALI.
 * Tenant baglami yoksa (header yok / gecersiz UUID) 400 TENANT_REQUIRED.
 *
 * Seed (V2__demo_note.sql):
 *   tenant A (11111111-...) -> 2 not, tenant B (22222222-...) -> 1 not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoNoteTenantIsolationTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Test
    void tenantA_yalnizcaKendiNotlariniGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].text", everyItem(startsWith("Tenant A"))));
    }

    @Test
    void tenantB_yalnizcaKendiNotunuGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].text", containsString("Tenant B")));
    }

    @Test
    void tenantAVerisi_tenantBBaglamindaGorunmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].text", not(hasItem(containsString("Tenant A")))));
    }

    @Test
    void headersizIstek_400TenantRequired_veHicVeriDokmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void gecersizUuidHeader_400TenantRequired_veHicVeriDokmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", "gecersiz-uuid-degil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
