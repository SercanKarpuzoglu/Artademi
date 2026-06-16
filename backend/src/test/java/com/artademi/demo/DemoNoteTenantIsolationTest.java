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
 * Tenant izolasyon kaniti: ayni endpoint, X-Tenant-Id header'ina gore yalnizca
 * o tenant'in verisini dondurmeli; bir tenant'in verisi digerine SIZMAMALI.
 *
 * Seed (V2__demo_note.sql): tenant 1 -> 2 not, tenant 2 -> 1 not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoNoteTenantIsolationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Test
    void tenant1_yalnizcaKendiNotlariniGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].text", everyItem(startsWith("Tenant 1"))));
    }

    @Test
    void tenant2_yalnizcaKendiNotunuGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].text", containsString("Tenant 2")));
    }

    @Test
    void tenant1Verisi_tenant2BaglamindaGorunmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes").header("X-Tenant-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].text", not(hasItem(containsString("Tenant 1")))));
    }

    @Test
    void headersizIstek_400TenantRequired_veHicVeriDokmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
