package com.artademi.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tenant izolasyon kaniti — JWT tabanli. Tenant artik JWT'deki {@code tenant_id}
 * claim'inden gelir; her tenant yalnizca kendi verisini gormeli, sizinti olmamali.
 *
 * <p>Gercek Keycloak gerekmez: {@code jwt()} post-processor authentication'i
 * dogrudan enjekte eder, mock {@link JwtDecoder} ise issuer-uri JWKS cagrisini
 * engeller.
 *
 * Seed (V2__demo_note.sql): tenant A (11111111-...) -> 2 not, tenant B (22222222-...) -> 1 not.
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

    // Gercek issuer-uri tabanli JwtDecoder yerine mock; jwt() post-processor decode kullanmaz.
    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    /** Verilen tenant_id ve ADMIN rolu ile kimligi dogrulanmis bir JWT enjekte eder. */
    private static RequestPostProcessor tenantToken(String tenantId) {
        return jwt().jwt(builder -> builder
                .claim("tenant_id", tenantId)
                .claim("realm_access", Map.of("roles", List.of("ADMIN"))));
    }

    @Test
    void tenantA_yalnizcaKendiNotlariniGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").with(tenantToken(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].text", everyItem(startsWith("Tenant A"))));
    }

    @Test
    void tenantB_yalnizcaKendiNotunuGorur() throws Exception {
        mockMvc.perform(get("/api/demo-notes").with(tenantToken(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].text", containsString("Tenant B")));
    }

    @Test
    void tenantAVerisi_tenantBBaglamindaGorunmez() throws Exception {
        mockMvc.perform(get("/api/demo-notes").with(tenantToken(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].text", not(hasItem(containsString("Tenant A")))));
    }

    @Test
    void tokenYok_401() throws Exception {
        mockMvc.perform(get("/api/demo-notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVarAmaTenantIdClaimYok_400TenantRequired_veVeriYok() throws Exception {
        RequestPostProcessor tokenWithoutTenant = jwt().jwt(builder -> builder
                .claim("realm_access", Map.of("roles", List.of("ADMIN"))));
        mockMvc.perform(get("/api/demo-notes").with(tokenWithoutTenant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
