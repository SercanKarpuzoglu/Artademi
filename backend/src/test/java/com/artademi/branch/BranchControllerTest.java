package com.artademi.branch;

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
 * Brans (Branch) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc +
 * JWT post-processor. Tenant izolasyonu (PK-find sizinti yok), liste/arama/aktif filtresi,
 * aktiflik degisimi, yetki ve validasyonu dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BranchControllerTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * Verilen tenant_id + roller ile kimligi dogrulanmis JWT. jwt() post-processor gercek
     * converter'i calistirmadigi icin authority'ler (ROLE_*) ELLE verilir.
     */
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

    private static RequestPostProcessor admin(String tenantId) {
        return token(tenantId, "ADMIN");
    }

    private long createBranch(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/branches")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aktif").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void tenantIzolasyonu_baskaTenantGoremezVe404() throws Exception {
        long idA = createBranch(TENANT_A, "{\"ad\":\"Bale\"}");

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/branches").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/branches/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void listeAramaVeAktifFiltresi() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        createBranch(tenant, "{\"ad\":\"Bale\"}");
        createBranch(tenant, "{\"ad\":\"Piyano\"}");

        // q araması (ad contains, case-insensitive).
        mockMvc.perform(get("/api/branches").param("q", "bal").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].ad").value("Bale"));

        // Hepsi aktif -> ?aktif=true -> 2.
        mockMvc.perform(get("/api/branches").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void patchActive_pasifFiltredeGorunurAktifFiltredeGorunmez() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long id = createBranch(tenant, "{\"ad\":\"Gitar\"}");

        mockMvc.perform(patch("/api/branches/{id}/active", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        mockMvc.perform(get("/api/branches").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/branches").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void teacher_postYapamaz_403() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .with(token(TENANT_A, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Yetkisiz\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void frontdesk_postYapamaz_403_ama_getYapabilir_200() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .with(token(TENANT_A, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Yetkisiz\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/branches").with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isOk());
    }

    @Test
    void validasyon_adBos_400() throws Exception {
        mockMvc.perform(post("/api/branches")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
