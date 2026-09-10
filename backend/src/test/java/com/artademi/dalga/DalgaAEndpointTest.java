package com.artademi.dalga;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Dalga A (9 Eylul talepleri) backend uclari: egitmen haftalik programi ({@code /api/schedules/mine}),
 * urun alis fiyati + stok giris/cikis, Gelirler ozeti (odeme + satis).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaAEndpointTest {

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

    private static RequestPostProcessor rol(String tenantId, String rol) {
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static RequestPostProcessor admin(String t) {
        return rol(t, "ADMIN");
    }

    /** TEACHER token: JWT sub = keycloakUserId eslesmesi. */
    private static RequestPostProcessor teacher(String t, String sub) {
        return jwt()
                .jwt(b -> b.subject(sub).claim("tenant_id", t)
                        .claim("realm_access", Map.of("roles", List.of("TEACHER"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_TEACHER"));
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long egitmen(String t, String ad, String sub) throws Exception {
        return postId(t, "/api/teachers", "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[],"
                + "\"keycloakUserId\":\"" + sub + "\"}");
    }

    private long grup(String t, String ad, long brans, long ogretmen, long salon) throws Exception {
        return postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}");
    }

    // ---------- /api/schedules/mine ----------

    @Test
    void schedulesMine_egitmenYalnizKendiAktifDerslerini_gorur() throws Exception {
        String t = UUID.randomUUID().toString();
        long brans = postId(t, "/api/branches", "{\"ad\":\"Bale\"}");
        long salonA = postId(t, "/api/rooms", "{\"ad\":\"A\"}");
        long salonB = postId(t, "/api/rooms", "{\"ad\":\"B\"}");
        long selin = egitmen(t, "Selin", "sub-selin");
        long mert = egitmen(t, "Mert", "sub-mert");
        long grupSelin = grup(t, "Bale A", brans, selin, salonA);
        long grupMert = grup(t, "Bale B", brans, mert, salonB);
        postId(t, "/api/schedules", "{\"grupId\":" + grupSelin
                + ",\"gun\":\"PAZARTESI\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");
        postId(t, "/api/schedules", "{\"grupId\":" + grupMert
                + ",\"gun\":\"SALI\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");

        mockMvc.perform(get("/api/schedules/mine").with(teacher(t, "sub-selin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].grup.id").value(grupSelin))
                .andExpect(jsonPath("$.data[0].gun").value("PAZARTESI"))
                .andExpect(jsonPath("$.data[0].ogretmen.id").value(selin));

        // Eslesmeyen sub -> hata degil, bos liste.
        mockMvc.perform(get("/api/schedules/mine").with(teacher(t, "sub-yok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Ofis rolleri bu ucu kullanmaz (genel liste var) -> 403.
        mockMvc.perform(get("/api/schedules/mine").with(admin(t)))
                .andExpect(status().isForbidden());
    }

    // ---------- urun alis fiyati + stok hareketi ----------

    @Test
    void urun_alisFiyati_veStokGirisCikis() throws Exception {
        String t = UUID.randomUUID().toString();
        long urun = postId(t, "/api/products",
                "{\"ad\":\"Mayo\",\"satisFiyati\":100.00,\"alisFiyati\":60.00,\"stokAdedi\":2}");

        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t)))
                .andExpect(jsonPath("$.data.alisFiyati").value(60.00))
                .andExpect(jsonPath("$.data.stokAdedi").value(2));

        mockMvc.perform(patch("/api/products/{id}/stok-hareket", urun).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"miktar\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stokAdedi").value(7));

        mockMvc.perform(patch("/api/products/{id}/stok-hareket", urun).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"miktar\":-3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stokAdedi").value(4));

        mockMvc.perform(patch("/api/products/{id}/stok-hareket", urun).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"miktar\":-10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/products/{id}/stok-hareket", urun).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"miktar\":0}"))
                .andExpect(status().isBadRequest());

        // Stok muhasebe isi degil: FRONTDESK -> 403.
        mockMvc.perform(patch("/api/products/{id}/stok-hareket", urun).with(rol(t, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"miktar\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---------- gelir ozeti ----------

    @Test
    void gelirOzeti_odemeVeSatisToplami_tarihAraligina_gore() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Ece\",\"soyad\":\"Test\","
                + "\"tcKimlikNo\":\"93000000001\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci + ",\"tutar\":300.00,"
                + "\"odemeTarihi\":\"2026-09-05\",\"odemeYontemi\":\"NAKIT\"}");
        long urun = postId(t, "/api/products", "{\"ad\":\"Kitap\",\"satisFiyati\":50.00,\"stokAdedi\":10}");
        postId(t, "/api/sales", "{\"urunId\":" + urun + ",\"adet\":2,\"satisTarihi\":\"2026-09-06\"}");

        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-01").param("to", "2026-09-30")
                        .with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.odemeToplam").value(300.00))
                .andExpect(jsonPath("$.data.satisToplam").value(100.00))
                .andExpect(jsonPath("$.data.toplam").value(400.00));

        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-10-01").param("to", "2026-10-31")
                        .with(rol(t, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toplam").value(0.00));

        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-30").param("to", "2026-09-01")
                        .with(admin(t)))
                .andExpect(status().isBadRequest());

        // Baska tenant'in gelirleri sizmaz.
        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-01").param("to", "2026-09-30")
                        .with(admin(UUID.randomUUID().toString())))
                .andExpect(jsonPath("$.data.toplam").value(0.00));

        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-01").param("to", "2026-09-30")
                        .with(rol(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }
}
