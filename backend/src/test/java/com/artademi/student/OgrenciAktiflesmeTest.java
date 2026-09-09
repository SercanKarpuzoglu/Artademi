package com.artademi.student;

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
 * Test ekibinin bildirdigi durum (2026-09-09): "Ogrenciyi gruba atiyorum, yoklamasini aliyorum;
 * ama aktif ogrenci listesinde cikmiyor."
 *
 * <p>URUN KARARI (ayni gun): statu gecisi OTOMATIK DEGIL. Yeni ogrenci DENEME dogar; gruba kayit ve
 * yoklama statuyu degistirmez; kurum deneme dersinden sonra ogrenciyi ELLE AKTIF yapar. Sistem
 * bunun unutulmamasi icin iki yerde uyarir: (1) grup ekraninda kayit yanitinda ogrenci statusu
 * doner (Deneme rozeti + uyari), (2) otomatik tahakkuk sonucu/onizlemesi atlanan DENEME
 * ogrencileri ayri listeler ("N deneme ogrencisi aidat almayacak").
 *
 * <p>Bu test o karari sabitler: DENEME'de kalma davranisi, uyari verileri ve elle AKTIF yapinca
 * tahakkugun uretilmesi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OgrenciAktiflesmeTest {

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

    private static RequestPostProcessor admin(String tenantId) {
        List<GrantedAuthority> yetkiler = Arrays.stream(new String[] {"ADMIN"})
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(yetkiler);
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long grupKur(String t) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Bale\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"H\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon\"}");
        return postId(t, "/api/groups", "{\"ad\":\"Bale Baslangic\",\"tip\":\"GRUP\",\"bransId\":"
                + brans + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon
                + ",\"aylikAidat\":500.00}");
    }

    /** Test ekibinin yaptigi akisin birebir aynisi. */
    private long ogrenciyiGrubaAtVeYoklamaAl(String t) throws Exception {
        long grup = grupKur(t);
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Zeynep\",\"soyad\":\"Test\","
                + "\"tcKimlikNo\":\"91000000001\",\"dogumTarihi\":\"2012-01-01\","
                + "\"yetiskinMi\":true}");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long oturum = postId(t, "/api/attendance-sessions",
                "{\"grupId\":" + grup + ",\"tarih\":\"" + LocalDate.now() + "\"}");
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrenci + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isOk());
        return ogrenci;
    }

    @Test
    void grubaAtilipYoklamasiAlinanOgrenci_DENEMEdeKalir_kayitYanitiStatuTasir() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciyiGrubaAtVeYoklamaAl(t);

        // Karar: statu degismez -> "Aktif" sekmesinde yok, "Deneme" sekmesinde var.
        mockMvc.perform(get("/api/students").param("status", "AKTIF").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").doesNotExist());
        mockMvc.perform(get("/api/students").param("status", "DENEME").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").exists());

        // Uyari 1: grup ekrani kayit listesinde ogrencinin statusu gorunur (Deneme rozeti).
        mockMvc.perform(get("/api/students/{id}/enrollments", ogrenci).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ogrenci.status").value("DENEME"));
    }

    @Test
    void otomatikTahakkuk_denemeOgrenciyiAtlar_uyariListesindeGosterir_aktifYapincaUretir()
            throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciyiGrubaAtVeYoklamaAl(t);
        String donem = YearMonth.now().toString();

        // Uyari 2: onizleme de uretim de DENEME ogrenciyi ayri listede doner; sayaclara karismaz.
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", donem).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0))
                .andExpect(jsonPath("$.data.atlananSayisi").value(0))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler.length()").value(1))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler[0].ogrenciId").value(ogrenci))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler[0].ad").value("Zeynep"))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler[0].grupAd").value("Bale Baslangic"));

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + donem + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler.length()").value(1));

        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data.length()").value(0));

        // Kurum uyariyi gorup ogrenciyi ELLE aktif yapar -> ayni donem tekrar uretilince tahakkuk cikar.
        mockMvc.perform(patch("/api/students/{id}/status", ogrenci).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + donem + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler.length()").value(0));

        mockMvc.perform(get("/api/students").param("status", "AKTIF").with(admin(t)))
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").exists());
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
