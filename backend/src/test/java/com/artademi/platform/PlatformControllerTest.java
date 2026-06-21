package com.artademi.platform;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Platform (SUPER_ADMIN) tenant yonetimi entegrasyon testleri. SUPER_ADMIN token'i tenant_id
 * claim'i TASIMAZ (platform-duzeyi); /api/platform/** interceptor'dan muaf oldugundan erisilebilir.
 * Diger roller 403; SUPER_ADMIN normal is uclarina (tenant gerektiren) 400 TENANT_REQUIRED alir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformControllerTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /** SUPER_ADMIN token: tenant_id claim'i YOK. */
    private static RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    /** Tenant'li normal rol token'i (tenant_id claim + roller). */
    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    @Test
    void superAdmin_listele_seedTenantGorunur() throws Exception {
        // V13 seed: Lina (1111) en az 1 tenant.
        // V13 seed (Lina) en az bir tenant garantiler.
        mockMvc.perform(get("/api/platform/tenants").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].createdAt").exists());
    }

    @Test
    void superAdmin_olustur_mukerrer_bos() throws Exception {
        // 201 + AKTIF + UUID
        String body = mockMvc.perform(post("/api/platform/tenants").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"Test Sanat\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ad").value("Test Sanat"))
                .andExpect(jsonPath("$.data.status").value("AKTIF"))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        java.util.UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());

        // Mukerrer ad (ignore-case) -> 409
        mockMvc.perform(post("/api/platform/tenants").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"test sanat\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        // Bos ad -> 400 + error.fields.ad
        mockMvc.perform(post("/api/platform/tenants").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.ad").exists());
    }

    @Test
    void superAdmin_durumDegistir_idempotent_ve404() throws Exception {
        // Yeni tenant olustur -> id al
        String body = mockMvc.perform(post("/api/platform/tenants").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"Durum Testi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        // AKTIF -> ASKIDA
        mockMvc.perform(patch("/api/platform/tenants/{id}/status", id).with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ASKIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASKIDA"));

        // Tekrar ASKIDA -> no-op, 200
        mockMvc.perform(patch("/api/platform/tenants/{id}/status", id).with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ASKIDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASKIDA"));

        // Bilinmeyen id -> 404
        mockMvc.perform(patch("/api/platform/tenants/{id}/status",
                        "99999999-9999-9999-9999-999999999999").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void normalAdmin_platformUclarina_403() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").with(token(TENANT_A, "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/platform/tenants").with(token(TENANT_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/platform/tenants/{id}/status", TENANT_A).with(token(TENANT_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ASKIDA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regresyon_normalAkisBozulmadi() throws Exception {
        // admin.a normal is ucu -> tenant'li, calisir (200).
        mockMvc.perform(get("/api/students").with(token(TENANT_A, "ADMIN")))
                .andExpect(status().isOk());
        // SUPER_ADMIN (tenant_id yok) is ucuna -> 400 TENANT_REQUIRED (iz modullerine erismez).
        mockMvc.perform(get("/api/students").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"));
    }
}
