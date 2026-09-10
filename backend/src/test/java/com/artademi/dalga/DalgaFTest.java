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

/** Dalga F: egitmen kalitesi raporu — yuk, planlanan vs alinan yoklama, katilim orani, yetki, tenant. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaFTest {

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

    private static RequestPostProcessor rol(String t, String rol) {
        return jwt()
                .jwt(b -> b.claim("tenant_id", t).claim("realm_access", Map.of("roles", List.of(rol))))
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

    @Test
    void egitmenKalitesi_yuk_planlananVsAlinan_katilimOrani() throws Exception {
        String t = UUID.randomUUID().toString();
        long brans = postId(t, "/api/branches", "{\"ad\":\"Bale\"}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon\"}");
        long selin = postId(t, "/api/teachers", "{\"ad\":\"Selin\",\"soyad\":\"Hoca\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long grup = postId(t, "/api/groups", "{\"ad\":\"Bale A\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + selin + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}");
        // Haftada 2 ders x 1,5 saat = 3 saat; 14 Eyl - 27 Eyl 2026 (2 hafta) -> 4 planlanan ders
        postId(t, "/api/schedules", "{\"grupId\":" + grup + ",\"gun\":\"PAZARTESI\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:30\"}");
        postId(t, "/api/schedules", "{\"grupId\":" + grup + ",\"gun\":\"CARSAMBA\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:30\"}");
        long o1 = postId(t, "/api/students", "{\"ad\":\"A\",\"soyad\":\"Bir\",\"tcKimlikNo\":\"91100000001\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        long o2 = postId(t, "/api/students", "{\"ad\":\"B\",\"soyad\":\"Iki\",\"tcKimlikNo\":\"91100000002\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        for (long o : new long[] {o1, o2}) {
            mockMvc.perform(patch("/api/students/{id}/status", o).with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"AKTIF\"}")).andExpect(status().isOk());
            postId(t, "/api/enrollments", "{\"ogrenciId\":" + o + ",\"grupId\":" + grup + ",\"kayitTarihi\":\"2026-09-01\"}");
        }
        // 2 oturum alindi (4 planlanan): biri kaydedildi (1 geldi 1 gelmedi), digeri acildi ama kaydedilmedi
        long s1 = postId(t, "/api/attendance-sessions", "{\"grupId\":" + grup + ",\"tarih\":\"2026-09-14\"}");
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", s1).with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + o1 + ",\"durum\":\"GELDI\"},{\"ogrenciId\":" + o2 + ",\"durum\":\"GELMEDI\"}]"))
                .andExpect(status().isOk());
        postId(t, "/api/attendance-sessions", "{\"grupId\":" + grup + ",\"tarih\":\"2026-09-16\"}");

        mockMvc.perform(get("/api/reports/teacher-quality").param("baslangic", "2026-09-14").param("bitis", "2026-09-27").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.satirlar", hasSize(1)))
                .andExpect(jsonPath("$.data.satirlar[0].ad").value("Selin"))
                .andExpect(jsonPath("$.data.satirlar[0].aktifGrup").value(1))
                .andExpect(jsonPath("$.data.satirlar[0].ogrenciSayisi").value(2))
                .andExpect(jsonPath("$.data.satirlar[0].haftalikDersSaati").value(3.0))
                .andExpect(jsonPath("$.data.satirlar[0].planlananDers").value(4))
                .andExpect(jsonPath("$.data.satirlar[0].oturumSayisi").value(2))
                .andExpect(jsonPath("$.data.satirlar[0].alinmayanYoklama").value(2))
                .andExpect(jsonPath("$.data.satirlar[0].kaydedilmemisOturum").value(1))
                // Kaydedilmemis oturumun varsayilan GELMEDI satirlari da sayilir: geldi 1, gelmedi 3 -> %25
                .andExpect(jsonPath("$.data.satirlar[0].geldi").value(1))
                .andExpect(jsonPath("$.data.satirlar[0].gelmedi").value(3))
                .andExpect(jsonPath("$.data.satirlar[0].katilimOrani").value(25.0));

        mockMvc.perform(get("/api/reports/teacher-quality").param("baslangic", "2026-09-14").param("bitis", "2026-09-27").with(rol(t, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/teacher-quality").param("baslangic", "2026-09-27").param("bitis", "2026-09-14").with(admin(t)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports/teacher-quality").param("baslangic", "2026-09-14").param("bitis", "2026-09-27").with(admin(UUID.randomUUID().toString())))
                .andExpect(jsonPath("$.data.satirlar", hasSize(0)));
    }
}
