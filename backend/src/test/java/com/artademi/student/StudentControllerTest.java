package com.artademi.student;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ogrenci Islemleri (2c-1) entegrasyon testleri — gercek PostgreSQL (Testcontainers) +
 * MockMvc + JWT post-processor. Veriyi API uzerinden (ADMIN token) olusturup tenant
 * izolasyonu, statu filtresi, kardes eslestirme, yetki ve validasyonu dogrular.
 *
 * <p>Gercek Keycloak gerekmez: {@code jwt()} authentication'i enjekte eder; mock
 * {@link JwtDecoder} issuer-uri JWKS cagrisini engeller. Tenant her istekte
 * {@code tenant_id} claim'inden gelir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentControllerTest {

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
     * Verilen tenant_id + roller ile kimligi dogrulanmis bir JWT enjekte eder.
     *
     * <p>jwt() post-processor gercek JwtAuthenticationConverter'i CALISTIRMAZ; bu yuzden
     * authority'ler elle verilir. Controller @PreAuthorize hasAnyRole('ADMIN') -> "ROLE_ADMIN"
     * authority bekler; realm_access claim'i de prod converter ile birebir ayni cikti icin korunur.
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

    /** Yetiskin bir ogrenci JSON'u (veli kurali bypass; testleri sade tutar). */
    private static String yetiskinJson(String ad, String soyad, String tc) {
        return """
                {"ad":"%s","soyad":"%s","tcKimlikNo":"%s","dogumTarihi":"1990-01-01","yetiskinMi":true}
                """.formatted(ad, soyad, tc);
    }

    /** Verilen baba TC'li (veli) ogrenci JSON'u. */
    private static String babaliJson(String ad, String soyad, String tc, String babaAd, String babaTc) {
        return """
                {"ad":"%s","soyad":"%s","tcKimlikNo":"%s","dogumTarihi":"2015-05-05",
                 "yetiskinMi":false,"babaAd":"%s","babaTcKimlikNo":"%s"}
                """.formatted(ad, soyad, tc, babaAd, babaTc);
    }

    private long createStudent(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/students")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DENEME"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("id").asLong();
    }

    // --- 1. Tenant izolasyonu ---

    @Test
    void tenantAVerisi_tenantBBaglamindaGorunmez() throws Exception {
        long idA = createStudent(TENANT_A, yetiskinJson("Ali", "Veli", "11111111111"));

        // B baglaminda GET -> 404 (filtre zaten bulamaz).
        mockMvc.perform(get("/api/students/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/students").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // --- 2. Statu filtresi ---

    @Test
    void statusFiltresi_yalnizcaIstenenStatuyuDondurur() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long aktifId = createStudent(tenant, yetiskinJson("Aktif", "Ogrenci", "30000000001"));
        createStudent(tenant, yetiskinJson("Deneme", "Ogrenci", "30000000002"));

        // Birini AKTIF yap; digeri DENEME kalir.
        mockMvc.perform(patch("/api/students/{id}/status", aktifId)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AKTIF"));

        mockMvc.perform(get("/api/students").param("status", "AKTIF").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("AKTIF"))));
    }

    // --- 3. Kardes eslestirme ---

    @Test
    void ayniBabaTcliIkiOgrenci_birbirininKardesidir() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long kardes1 = createStudent(tenant, babaliJson("Bir", "Kardes", "40000000001", "Baba", "49999999999"));
        long kardes2 = createStudent(tenant, babaliJson("Iki", "Kardes", "40000000002", "Baba", "49999999999"));

        mockMvc.perform(get("/api/students/{id}/siblings", kardes1).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(kardes2));

        mockMvc.perform(get("/api/students/{id}/siblings", kardes2).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(kardes1));
    }

    @Test
    void bosVeliTcli_kardesEslesmesiYok() throws Exception {
        String tenant = "55555555-5555-5555-5555-555555555555";
        // Iki yetiskin, veli TC'leri bos -> birbirine kardes OLMAMALI.
        long y1 = createStudent(tenant, yetiskinJson("Tek", "Bir", "50000000001"));
        createStudent(tenant, yetiskinJson("Tek", "Iki", "50000000002"));

        mockMvc.perform(get("/api/students/{id}/siblings", y1).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // --- 4. Yetki: TEACHER erisemez (403) ---

    @Test
    void teacher_postYapamaz_403() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(token(TENANT_A, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yetiskinJson("Yetkisiz", "Ogretmen", "60000000001")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_listeyiGoremez_403() throws Exception {
        mockMvc.perform(get("/api/students").with(token(TENANT_A, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    // --- 5. Validasyon ---

    @Test
    void adEksik_400ValidationError() throws Exception {
        String json = """
                {"soyad":"Soyad","tcKimlikNo":"70000000001","dogumTarihi":"2000-01-01","yetiskinMi":true}
                """;
        mockMvc.perform(post("/api/students")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.ad").exists());
    }

    @Test
    void yetiskinDegilVeliYok_400ValidationError() throws Exception {
        String json = """
                {"ad":"Cocuk","soyad":"Velisiz","tcKimlikNo":"70000000002","dogumTarihi":"2016-01-01","yetiskinMi":false}
                """;
        mockMvc.perform(post("/api/students")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.veli").exists());
    }
}
