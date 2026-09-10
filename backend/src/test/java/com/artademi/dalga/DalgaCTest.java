package com.artademi.dalga;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.bildirim.OtomatikBildirimService;
import com.artademi.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * Dalga C: egitmen kilidi (bir kez Kaydet, sonra yonetici duzeltir), IZINLI yalniz ofis, "yoklama alindi"
 * uygulama ici bildirimi (ofise), "yoklama alinmadi" isi (egitmene + ofise), zil uclari.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaCTest {

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

    @Autowired
    OtomatikBildirimService otomatikBildirim;

    private static RequestPostProcessor rol(String t, String rol, String sub) {
        return jwt()
                .jwt(b -> b.subject(sub).claim("tenant_id", t).claim("preferred_username", sub)
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static RequestPostProcessor admin(String t) {
        return rol(t, "ADMIN", "sub-admin");
    }

    private long postId(String t, String yol, String json, RequestPostProcessor kim) throws Exception {
        String body = mockMvc.perform(post(yol).with(kim)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** brans + salon + egitmen(sub) + grup + kayitli ogrenci; [grup, ogrenci, egitmen] doner. */
    private long[] kurulum(String t, String ad, String sub) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}", admin(t));
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}", admin(t));
        long egitmen = postId(t, "/api/teachers", "{\"ad\":\"Selin\",\"soyad\":\"" + ad + "\",\"email\":\"selin@test.local\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[],"
                + "\"keycloakUserId\":\"" + sub + "\"}", admin(t));
        long grup = postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + egitmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}", admin(t));
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Ece\",\"soyad\":\"" + ad + "\",\"tcKimlikNo\":\"96"
                + String.format("%09d", Math.abs(ad.hashCode()) % 1_000_000_000) + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}",
                admin(t));
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}", admin(t));
        return new long[] {grup, ogrenci, egitmen};
    }

    @Test
    void egitmen_birKezKaydeder_sonraKilit_yoneticiDuzeltir_izinliYalnizOfis_ofiseBildirim() throws Exception {
        String t = UUID.randomUUID().toString();
        String sub = "sub-selin-" + t.substring(0, 8);
        long[] k = kurulum(t, "Bale", sub);
        RequestPostProcessor egitmen = rol(t, "TEACHER", sub);
        LocalDate bugun = LocalDate.now();

        long oturum = postId(t, "/api/attendance-sessions", "{\"grupId\":" + k[0] + ",\"tarih\":\"" + bugun + "\"}", egitmen);
        mockMvc.perform(get("/api/attendance-sessions/{id}", oturum).with(egitmen))
                .andExpect(jsonPath("$.data.kaydedildi").value(false));

        // Egitmen IZINLI isaretleyemez
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(egitmen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + k[1] + ",\"durum\":\"IZINLI\"}]"))
                .andExpect(status().isBadRequest());

        // Ilk kayit: 200, kaydedildi, kaydeden
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(egitmen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + k[1] + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kaydedildi").value(true))
                .andExpect(jsonPath("$.data.kaydeden").value(sub))
                .andExpect(jsonPath("$.data.entries[0].durum").value("GELDI"));

        // Ikinci kayit egitmene KILITLI
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(egitmen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + k[1] + ",\"durum\":\"GELMEDI\"}]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KILITLI"));

        // Yonetici duzeltir (IZINLI dahil)
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + k[1] + ",\"durum\":\"IZINLI\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].durum").value("IZINLI"))
                .andExpect(jsonPath("$.data.kaydeden").value("sub-admin"));

        // Ofise "yoklama alindi" bildirimi (bir kez — yonetici duzeltmesi uretmez); egitmen gormez
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.okunmamis").value(1))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(1)))
                .andExpect(jsonPath("$.data.bildirimler[0].tip").value("YOKLAMA_ALINDI"))
                .andExpect(jsonPath("$.data.bildirimler[0].baslik").value("Bale yoklaması alındı"))
                .andExpect(jsonPath("$.data.bildirimler[0].metin").value(containsString("1/1 öğrenci geldi")));
        mockMvc.perform(get("/api/bildirimler").with(rol(t, "FRONTDESK", "sub-fd")))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(1)));
        mockMvc.perform(get("/api/bildirimler").with(egitmen))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(0)));

        // Okundu isareti
        String body = mockMvc.perform(get("/api/bildirimler").with(admin(t))).andReturn().getResponse().getContentAsString();
        long bildirimId = objectMapper.readTree(body).path("data").path("bildirimler").get(0).path("id").asLong();
        mockMvc.perform(post("/api/bildirimler/{id}/okundu", bildirimId).with(admin(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(jsonPath("$.data.okunmamis").value(0))
                .andExpect(jsonPath("$.data.bildirimler[0].okundu").value(true));

        // Baska tenant gormez
        mockMvc.perform(get("/api/bildirimler").with(admin(UUID.randomUUID().toString())))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(0)));

        // Yoklama listesi: tarih araligi + kaydedildi alani
        mockMvc.perform(get("/api/attendance-sessions").param("from", bugun.minusDays(1).toString())
                        .param("to", bugun.toString()).with(egitmen))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].kaydedildi").value(true));
        mockMvc.perform(get("/api/attendance-sessions").param("from", bugun.plusDays(1).toString())
                        .param("to", bugun.plusDays(2).toString()).with(egitmen))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void yoklamaAlinmadi_isi_oturumsuzDersIcin_egitmeneVeOfise_bildirimUretir() throws Exception {
        String t = UUID.randomUUID().toString();
        String subA = "sub-a-" + t.substring(0, 8);
        String subB = "sub-b-" + t.substring(0, 8);
        long[] a = kurulum(t, "Piyano", subA);
        long[] b = kurulum(t, "Gitar", subB);
        LocalDate bugun = LocalDate.now();
        String gun = java.time.DayOfWeek.from(bugun).name(); // MONDAY...
        String haftaGunu = switch (gun) {
            case "MONDAY" -> "PAZARTESI"; case "TUESDAY" -> "SALI"; case "WEDNESDAY" -> "CARSAMBA";
            case "THURSDAY" -> "PERSEMBE"; case "FRIDAY" -> "CUMA"; case "SATURDAY" -> "CUMARTESI";
            default -> "PAZAR";
        };
        // Her iki grubun da bugun dersi var; yalniz B'nin oturumu acilmis.
        postId(t, "/api/schedules", "{\"grupId\":" + a[0] + ",\"gun\":\"" + haftaGunu + "\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}", admin(t));
        postId(t, "/api/schedules", "{\"grupId\":" + b[0] + ",\"gun\":\"" + haftaGunu + "\",\"baslangicSaati\":\"12:00\",\"bitisSaati\":\"13:00\"}", admin(t));
        postId(t, "/api/attendance-sessions", "{\"grupId\":" + b[0] + ",\"tarih\":\"" + bugun + "\"}", admin(t));

        TenantContext.set(UUID.fromString(t));
        try {
            int n = otomatikBildirim.yoklamaAlinmadi(bugun, false);
            org.junit.jupiter.api.Assertions.assertEquals(1, n);
        } finally {
            TenantContext.clear();
        }

        // Egitmen A kendi bildirimini gorur; egitmen B gormez; ofis bilgilendirilir
        mockMvc.perform(get("/api/bildirimler").with(rol(t, "TEACHER", subA)))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(1)))
                .andExpect(jsonPath("$.data.bildirimler[0].tip").value("YOKLAMA_ALINMADI"))
                .andExpect(jsonPath("$.data.bildirimler[0].baslik").value("Piyano dersinin yoklaması alınmadı"));
        mockMvc.perform(get("/api/bildirimler").with(rol(t, "TEACHER", subB)))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(0)));
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(jsonPath("$.data.bildirimler", hasSize(1)))
                .andExpect(jsonPath("$.data.bildirimler[0].baslik").value("Selin Piyano — Piyano yoklaması alınmadı"));

        // Hepsini okundu
        mockMvc.perform(post("/api/bildirimler/okundu-hepsi").with(admin(t)))
                .andExpect(jsonPath("$.data").value(1));
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(jsonPath("$.data.okunmamis").value(0));
    }
}
