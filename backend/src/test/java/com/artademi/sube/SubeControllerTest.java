package com.artademi.sube;

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
 * Sube (fiziksel lokasyon) entegrasyon testleri — gercek PostgreSQL + MockMvc + JWT.
 *
 * <p>En kritik iki test, subenin BASKA modullerle baglantisinda: salon/grup uzerinden
 * capraz-tenant sube referansi kurulamamalidir. FK bunu ENGELLEMEZ; koruma servisteki
 * {@code findScopedById} cozumlemesinden gelir, o yuzden burada aciken dogrulanir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SubeControllerTest {

    private static final String TENANT_A = "aaaaaaaa-1111-1111-1111-111111111111";
    private static final String TENANT_B = "bbbbbbbb-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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

    private long createSube(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/subeler")
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
        long idA = createSube(TENANT_A, "{\"ad\":\"Kadıköy Şubesi\",\"adres\":\"Moda Cad. 1\"}");

        mockMvc.perform(get("/api/subeler").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // PK-find sizintisi OLMAMALI.
        mockMvc.perform(get("/api/subeler/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void salonSubeyeBaglanir_yanittaSubeAdiDoner() throws Exception {
        String tenant = "cccccccc-3333-3333-3333-333333333333";
        long subeId = createSube(tenant, "{\"ad\":\"Merkez\"}");

        mockMvc.perform(post("/api/rooms")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Stüdyo 1\",\"subeId\":" + subeId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subeId").value(subeId))
                // Ad da donuyor ki istemci sube adini gostermek icin ikinci istek atmasin.
                .andExpect(jsonPath("$.data.subeAd").value("Merkez"));
    }

    @Test
    void salonaBaskaTenantinSubesiAtanamaz_404() throws Exception {
        long subeA = createSube(TENANT_A, "{\"ad\":\"A Şubesi\"}");

        // B, A'nin sube id'siyle salon acmaya calisiyor: FK gecerli ama TENANT yanlis.
        mockMvc.perform(post("/api/rooms")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Sızıntı\",\"subeId\":" + subeA + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void listeAramaVeAktifFiltresi() throws Exception {
        String tenant = "dddddddd-4444-4444-4444-444444444444";
        createSube(tenant, "{\"ad\":\"Kadıköy\"}");
        createSube(tenant, "{\"ad\":\"Beşiktaş\"}");

        mockMvc.perform(get("/api/subeler").param("q", "kadı").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].ad").value("Kadıköy"));

        mockMvc.perform(get("/api/subeler").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void patchActive_pasifFiltredeGorunurAktifFiltredeGorunmez() throws Exception {
        String tenant = "eeeeeeee-5555-5555-5555-555555555555";
        long id = createSube(tenant, "{\"ad\":\"Kapanan Şube\"}");

        mockMvc.perform(patch("/api/subeler/{id}/active", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        mockMvc.perform(get("/api/subeler").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/subeler").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void teacher_postYapamaz_403() throws Exception {
        mockMvc.perform(post("/api/subeler")
                        .with(token(TENANT_A, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Yetkisiz\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void frontdesk_postYapamaz_403_ama_getYapabilir_200() throws Exception {
        mockMvc.perform(post("/api/subeler")
                        .with(token(TENANT_A, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Yetkisiz\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/subeler").with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isOk());
    }

    @Test
    void validasyon_adBos_400() throws Exception {
        mockMvc.perform(post("/api/subeler")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
