package com.artademi.tedarikci;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tedarikci modulu.
 *
 * <p>Kilitlenen sey: "kime ne kadar odedik" toplaminin giderlerden HESAPLANMASI (saklanmamasi)
 * ve kurum izolasyonu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TedarikciEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> yetkiler = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(yetkiler);
    }

    private static RequestPostProcessor admin(String t) {
        return token(t, "ADMIN");
    }

    private static String yeniKurum() {
        return UUID.randomUUID().toString();
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void olustur_veListele() throws Exception {
        String t = yeniKurum();
        postId(t, "/api/tedarikciler", "{\"ad\":\"Kırtasiye A.Ş.\",\"telefon\":\"02121112233\"}");

        mockMvc.perform(get("/api/tedarikciler").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ad").value("Kırtasiye A.Ş."))
                .andExpect(jsonPath("$.data[0].toplamOdenen").value(0));
    }

    @Test
    void toplamOdenen_giderlerdenHESAPLANIR() throws Exception {
        String t = yeniKurum();
        long tedarikci = postId(t, "/api/tedarikciler", "{\"ad\":\"Temizlik Ltd\"}");

        postId(t, "/api/expenses", "{\"tutar\":150.00,\"kategori\":\"Temizlik\","
                + "\"tedarikciId\":" + tedarikci + "}");
        postId(t, "/api/expenses", "{\"tutar\":250.00,\"kategori\":\"Temizlik\","
                + "\"tedarikciId\":" + tedarikci + "}");

        mockMvc.perform(get("/api/tedarikciler/{id}", tedarikci).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toplamOdenen").value(400.00));
    }

    @Test
    void tedarikcisizGider_toplamaGIRMEZ() throws Exception {
        String t = yeniKurum();
        long tedarikci = postId(t, "/api/tedarikciler", "{\"ad\":\"Kira\"}");
        postId(t, "/api/expenses", "{\"tutar\":9999.00,\"kategori\":\"Diğer\"}");

        mockMvc.perform(get("/api/tedarikciler/{id}", tedarikci).with(admin(t)))
                .andExpect(jsonPath("$.data.toplamOdenen").value(0));
    }

    @Test
    void baskaKurumunTedarikcisi_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long tedarikci = postId(a, "/api/tedarikciler", "{\"ad\":\"A Tedarikçi\"}");

        mockMvc.perform(get("/api/tedarikciler/{id}", tedarikci).with(admin(b)))
                .andExpect(status().isNotFound());
    }

    @Test
    void baskaKurumunTedarikcisineGider_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long aTedarikci = postId(a, "/api/tedarikciler", "{\"ad\":\"A Tedarikçi\"}");

        mockMvc.perform(post("/api/expenses").with(admin(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutar\":100.00,\"tedarikciId\":" + aTedarikci + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ayniIsimdeIkinci_409() throws Exception {
        String t = yeniKurum();
        postId(t, "/api/tedarikciler", "{\"ad\":\"Tekrar\"}");

        mockMvc.perform(post("/api/tedarikciler").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Tekrar\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onBuro_ERISEMEZ_403() throws Exception {
        // Yanit "toplam odenen" tasidigi icin PARASALDIR.
        String t = yeniKurum();
        mockMvc.perform(get("/api/tedarikciler").with(token(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }
}
