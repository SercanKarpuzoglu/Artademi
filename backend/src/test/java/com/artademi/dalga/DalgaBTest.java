package com.artademi.dalga;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Dalga B: zengin ogrenci listesi (gruplar, bakiye, devamsizlik serisi, kara liste) ve kara liste
 * akisi (isaretleme, gruba yazarken 409 KARA_LISTE, onayla ekleme).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaBTest {

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
                .jwt(b -> b.claim("tenant_id", tenantId).claim("preferred_username", "test." + rol.toLowerCase())
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static RequestPostProcessor admin(String t) {
        return rol(t, "ADMIN");
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long grupKur(String t, String ad) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Bale " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        return postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}");
    }

    private long ogrenci(String t, String ad, String tc) throws Exception {
        return postId(t, "/api/students", "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
    }

    private void yoklama(String t, long grup, long ogrenci, LocalDate tarih, String durum) throws Exception {
        long oturum = postId(t, "/api/attendance-sessions", "{\"grupId\":" + grup + ",\"tarih\":\"" + tarih + "\"}");
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrenci + ",\"durum\":\"" + durum + "\"}]"))
                .andExpect(status().isOk());
    }

    @Test
    void liste_gruplar_bakiye_devamsizlikSerisi_rolBazliPara() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = grupKur(t, "Bale Baslangic");
        long ogrenci = ogrenci(t, "Zeynep", "94000000001");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        mockMvc.perform(patch("/api/students/{id}/status", ogrenci).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}")).andExpect(status().isOk());
        // Tahakkuk 500 (otomatik) - odeme 200 = bakiye 300
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"donem\":\"" + YearMonth.now() + "\"}")).andExpect(status().isOk());
        postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci + ",\"tutar\":200.00,\"odemeTarihi\":\""
                + LocalDate.now() + "\",\"odemeYontemi\":\"NAKIT\"}");
        // Son iki ders GELMEDI, ondan onceki GELDI -> seri -2
        LocalDate bugun = LocalDate.now();
        yoklama(t, grup, ogrenci, bugun.minusDays(7), "GELDI");
        yoklama(t, grup, ogrenci, bugun.minusDays(3), "GELMEDI");
        yoklama(t, grup, ogrenci, bugun, "GELMEDI");

        mockMvc.perform(get("/api/students/liste").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ogrenci))
                .andExpect(jsonPath("$.data[0].status").value("AKTIF"))
                .andExpect(jsonPath("$.data[0].gruplar", hasSize(1)))
                .andExpect(jsonPath("$.data[0].gruplar[0].ad").value("Bale Baslangic"))
                .andExpect(jsonPath("$.data[0].bakiye").value(300.00))
                .andExpect(jsonPath("$.data[0].devamsizlikSerisi").value(-2))
                .andExpect(jsonPath("$.data[0].karaListe").value(false));

        // On buro: parasal alan HIC gelmez (null), diger sutunlar gelir.
        mockMvc.perform(get("/api/students/liste").with(rol(t, "FRONTDESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bakiye").doesNotExist())
                .andExpect(jsonPath("$.data[0].devamsizlikSerisi").value(-2));

        // Hic yoklamasi olmayan ogrenci: seri null, grup yok
        long yeni = ogrenci(t, "Ali", "94000000002");
        mockMvc.perform(get("/api/students/liste").param("q", "Ali").with(admin(t)))
                .andExpect(jsonPath("$.data[0].id").value(yeni))
                .andExpect(jsonPath("$.data[0].gruplar", hasSize(0)))
                .andExpect(jsonPath("$.data[0].devamsizlikSerisi").doesNotExist())
                .andExpect(jsonPath("$.data[0].bakiye").value(0.00));

        mockMvc.perform(get("/api/students/liste").with(rol(t, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void karaListe_isaretle_grubaYazarkenUyar_onaylaEkle_cikar() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = grupKur(t, "Piyano");
        long ogrenci = ogrenci(t, "Can", "94000000003");

        // Aciklamasiz alma 400
        mockMvc.perform(patch("/api/students/{id}/kara-liste", ogrenci).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"karaListe\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/students/{id}/kara-liste", ogrenci).with(rol(t, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"karaListe\":true,\"aciklama\":\"Ödeme yapmadan ayrıldı\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.karaListe").value(true))
                .andExpect(jsonPath("$.data.karaListeAciklama").value("Ödeme yapmadan ayrıldı"))
                .andExpect(jsonPath("$.data.karaListeEkleyen").value("test.frontdesk"))
                .andExpect(jsonPath("$.data.karaListeTarihi").exists());

        // Listede isaret gorunur
        mockMvc.perform(get("/api/students/liste").with(admin(t)))
                .andExpect(jsonPath("$.data[0].karaListe").value(true))
                .andExpect(jsonPath("$.data[0].karaListeAciklama").value("Ödeme yapmadan ayrıldı"));

        // Gruba yazarken onaysiz -> 409 KARA_LISTE, sebep mesajda
        mockMvc.perform(post("/api/enrollments").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KARA_LISTE"))
                .andExpect(jsonPath("$.error.message").value("Kara listede: Ödeme yapmadan ayrıldı"));

        // Onayla -> 201
        mockMvc.perform(post("/api/enrollments").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + ",\"karaListeOnayi\":true}"))
                .andExpect(status().isCreated());

        // Cikar -> alanlar temiz
        mockMvc.perform(patch("/api/students/{id}/kara-liste", ogrenci).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"karaListe\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.karaListe").value(false))
                .andExpect(jsonPath("$.data.karaListeAciklama").doesNotExist())
                .andExpect(jsonPath("$.data.karaListeEkleyen").doesNotExist());

        // Baska tenant'in ogrencisi -> 404
        mockMvc.perform(patch("/api/students/{id}/kara-liste", ogrenci).with(admin(UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"karaListe\":true,\"aciklama\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
