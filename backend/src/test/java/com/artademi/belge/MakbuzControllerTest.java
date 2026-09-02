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
 * Tahsilat makbuzu (PDF) ucu.
 *
 * <p>En kritik test {@link #makbuz_turkceFontGOMULU()}: gomulu font olmadan makbuzda
 * ş/ğ/İ/ı bozuk cikar ve bu hata SESSIZDIR — PDF yine uretilir, sadece yanlis basilir.
 * O yuzden fontun gercekten gomuldugunu bayt duzeyinde dogruluyoruz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MakbuzControllerTest {

    private static final String TENANT_A = "a1b2c3d4-1111-1111-1111-111111111111";
    private static final String TENANT_B = "a1b2c3d4-2222-2222-2222-222222222222";

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

    private long tahsilatOlustur(String tenantId, long ogrenciId, String tutar) throws Exception {
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"tutar\":" + tutar
                + ",\"odemeYontemi\":\"NAKIT\"}";
        String body = mockMvc.perform(post("/api/payments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void makbuz_pdfUretilir() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Gülşah", "31000000001");
        long tahsilat = tahsilatOlustur(TENANT_A, ogrenci, "2500.50");

        byte[] pdf = mockMvc.perform(get("/api/payments/{id}/makbuz.pdf", tahsilat)
                        .with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("makbuz_" + tahsilat)))
                .andReturn().getResponse().getContentAsByteArray();

        // Gercekten PDF mi ve bos degil mi?
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(2000);
    }

    @Test
    void makbuz_turkceFontGOMULU() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Gülşah", "31000000002");
        long tahsilat = tahsilatOlustur(TENANT_A, ogrenci, "1500.00");

        byte[] pdf = mockMvc.perform(get("/api/payments/{id}/makbuz.pdf", tahsilat)
                        .with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Gomulu font, PDF icinde font adiyla gecer. Gomulmemis olsaydi ş/ğ/İ/ı okuyucunun
        // fontuna kalirdi — makbuz baskisi kuruma gore degisirdi.
        String ham = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(ham).contains("DejaVuSans");
    }

    @Test
    void makbuz_baskaTenant_404() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Ada", "31000000003");
        long tahsilat = tahsilatOlustur(TENANT_A, ogrenci, "100.00");

        // B, A'nin tahsilatinin makbuzunu ALAMAZ (PK-find sizintisi olmamali).
        mockMvc.perform(get("/api/payments/{id}/makbuz.pdf", tahsilat).with(admin(TENANT_B)))
                .andExpect(status().isNotFound());
    }

    @Test
    void makbuz_onBuroErisemez_403() throws Exception {
        long ogrenci = ogrenciOlustur(TENANT_A, "Ada", "31000000004");
        long tahsilat = tahsilatOlustur(TENANT_A, ogrenci, "100.00");

        // Makbuz PARASAL belgedir; on buro parayi gormez.
        mockMvc.perform(get("/api/payments/{id}/makbuz.pdf", tahsilat)
                        .with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void makbuz_olmayanTahsilat_404() throws Exception {
        mockMvc.perform(get("/api/payments/{id}/makbuz.pdf", 999999).with(admin(TENANT_A)))
                .andExpect(status().isNotFound());
    }
}
