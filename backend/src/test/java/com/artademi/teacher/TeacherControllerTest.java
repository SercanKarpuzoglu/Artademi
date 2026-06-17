package com.artademi.teacher;

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
 * Ogretmen (Teacher) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc +
 * JWT post-processor. Tenant izolasyonu (PK-find + capraz-tenant brans sizinti yok), coklu
 * brans atamasi + bransId filtresi, hakedis validasyonu, aktif filtresi/PATCH active, yetki
 * ve temel validasyonu dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TeacherControllerTest {

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

    private long createBranch(String tenantId, String ad) throws Exception {
        String body = mockMvc.perform(post("/api/branches")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + ad + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Gecerli SAATLIK ogretmen JSON'u (istenirse bransIds eklenir). */
    private String saatlikJson(String bransIdsJsonArray) {
        String branslar = bransIdsJsonArray == null ? "[]" : bransIdsJsonArray;
        return "{\"ad\":\"Ayşe\",\"soyad\":\"Yılmaz\",\"hakedisTipi\":\"SAATLIK\","
                + "\"saatlikUcret\":200.00,\"bransIds\":" + branslar + "}";
    }

    private long createTeacher(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/teachers")
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
        long idA = createTeacher(TENANT_A, saatlikJson(null));

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/teachers").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/teachers/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void cokluBrans_atamaVeBransIdFiltresi() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long bransPiyano = createBranch(tenant, "Piyano");
        long bransGitar = createBranch(tenant, "Gitar");

        long teacherId = createTeacher(tenant,
                saatlikJson("[" + bransPiyano + "," + bransGitar + "]"));

        // TeacherResponse.branslar 2 oge.
        mockMvc.perform(get("/api/teachers/{id}", teacherId).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branslar", hasSize(2)));

        // ?bransId=<biri> o ogretmeni dondurur.
        mockMvc.perform(get("/api/teachers").param("bransId", String.valueOf(bransPiyano)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(teacherId));
    }

    @Test
    void baskaTenantBransIdsiyleOlusturma_404_sizintiYok() throws Exception {
        // Tenant A'da brans olustur.
        long bransA = createBranch(TENANT_A, "Keman");

        // Tenant B token'iyla A'nin brans id'siyle POST -> 404 (sizinti yok).
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saatlikJson("[" + bransA + "]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void hakedisValidasyonu_saatlikUcretYok_400() throws Exception {
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ayşe\",\"soyad\":\"Yılmaz\",\"hakedisTipi\":\"SAATLIK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.saatlikUcret").exists());
    }

    @Test
    void hakedisValidasyonu_ciroOraniGecersiz_400() throws Exception {
        // oran 0 -> gecersiz.
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ali\",\"soyad\":\"Demir\",\"hakedisTipi\":\"CIRO_ORANI\",\"ciroOrani\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.ciroOrani").exists());

        // oran > 100 -> gecersiz.
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ali\",\"soyad\":\"Demir\",\"hakedisTipi\":\"CIRO_ORANI\",\"ciroOrani\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.ciroOrani").exists());
    }

    @Test
    void patchActive_pasifFiltredeGorunurAktifFiltredeGorunmez() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long id = createTeacher(tenant, saatlikJson(null));

        mockMvc.perform(patch("/api/teachers/{id}/active", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        mockMvc.perform(get("/api/teachers").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/teachers").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void yetki_teacherPost403_frontdeskPost403_frontdeskGet200_adminPost201() throws Exception {
        // TEACHER POST -> 403.
        mockMvc.perform(post("/api/teachers")
                        .with(token(TENANT_A, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saatlikJson(null)))
                .andExpect(status().isForbidden());

        // FRONTDESK POST -> 403.
        mockMvc.perform(post("/api/teachers")
                        .with(token(TENANT_A, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saatlikJson(null)))
                .andExpect(status().isForbidden());

        // FRONTDESK GET -> 200.
        mockMvc.perform(get("/api/teachers").with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isOk());

        // ADMIN POST -> 201.
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saatlikJson(null)))
                .andExpect(status().isCreated());
    }

    @Test
    void validasyon_adSoyadBos_400() throws Exception {
        mockMvc.perform(post("/api/teachers")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"\",\"soyad\":\"\",\"hakedisTipi\":\"SAATLIK\",\"saatlikUcret\":200.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
