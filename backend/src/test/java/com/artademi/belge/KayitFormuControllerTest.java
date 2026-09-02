package com.artademi.belge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
 * Ogrenci kayit formu (PDF) ucu.
 *
 * <p>Formda PARASAL bilgi yoktur; makbuzdan farkli olarak on buro da basabilmelidir.
 * Bu ayrimi {@link #kayitFormu_onBuroDaBasabilir_200()} kilitler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KayitFormuControllerTest {

    private static final String TENANT_A = "c3d4e5f6-1111-1111-1111-111111111111";
    private static final String TENANT_B = "c3d4e5f6-2222-2222-2222-222222222222";

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
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private static RequestPostProcessor admin(String tenantId) {
        return token(tenantId, "ADMIN");
    }

    /** Turkce karakter iceren ad BILINCLI: makbuzda dogru basildigini garanti altina alir. */
    private long ogrenciOlustur(String tenantId, String ad, String tc) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Şahin\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"1990-01-01\",\"yetiskinMi\":true}";
        String body = mockMvc.perform(post("/api/students")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void kayitFormu_pdfUretilir() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Gülşah", "41000000001");

        byte[] pdf = mockMvc.perform(get("/api/students/{id}/kayit-formu.pdf", ogrenci)
                        .with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("kayit_formu")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(2000);
        // Gomulu font olmadan ş/ğ/İ/ı bozuk basilir ve hata SESSIZDIR.
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("DejaVuSans");
    }

    @Test
    void kayitFormu_onBuroDaBasabilir_200() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Ada", "41000000002");

        // Makbuzdan FARKLI: formda para yok, on buro basabilir.
        mockMvc.perform(get("/api/students/{id}/kayit-formu.pdf", ogrenci)
                        .with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isOk());
    }

    @Test
    void kayitFormu_baskaTenant_404() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Ada", "41000000003");

        mockMvc.perform(get("/api/students/{id}/kayit-formu.pdf", ogrenci).with(admin(TENANT_B)))
                .andExpect(status().isNotFound());
    }

    @Test
    void kayitFormu_olmayanOgrenci_404() throws Exception {
        mockMvc.perform(get("/api/students/{id}/kayit-formu.pdf", 999999).with(admin(TENANT_A)))
                .andExpect(status().isNotFound());
    }
}
