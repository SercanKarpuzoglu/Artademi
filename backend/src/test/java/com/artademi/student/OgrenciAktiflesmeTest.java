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
 * Test ekibinin bildirdigi durum (2026-09-09): "Ogrenciyi gruba atiyorum, yoklamasini aliyorum; ama aktif ogrenci
 * listesinde cikmiyor."
 *
 * <p>URUN KARARI (2026-09-12, oncekini degistirir): PLAN SECIMI STATUYU BELIRLER. Gruba yazarken Aylik/Donemlik
 * secilirse (veya ozel derse yazilirsa) ogrenci otomatik AKTIF olur ve kredi/tahakkuk acilir. "Deneme dersi"
 * plani secilirse DENEME kalir, para/kredi yok, yoklama alinabilir; "Plana gecir" ile Aylik/Donemlik'e cevrilince
 * AKTIF olur. (10 Eylul'deki "elle kalsin" karari Dalga E'nin plan modaliyla anlamsizlasmisti.)
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
    void grubaAtilipYoklamasiAlinanOgrenci_AKTIF_olur_listedeGorunur_aidatAlir() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciyiGrubaAtVeYoklamaAl(t); // plan verilmedi -> AYLIK -> AKTIF

        mockMvc.perform(get("/api/students").param("status", "AKTIF").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").exists());
        mockMvc.perform(get("/api/students/{id}", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.status").value("AKTIF"));
        mockMvc.perform(get("/api/students/{id}/enrollments", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data[0].ogrenci.status").value("AKTIF"))
                .andExpect(jsonPath("$.data[0].odemePlani").value("AYLIK"));

        // Aylik aidat uretilir
        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + YearMonth.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler.length()").value(0));
    }

    @Test
    void denemeDersiPlani_DENEMEdeKalir_paraYok_planaGecirince_AKTIF_veTahakkuk() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = grupKur(t);
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Zeynep\",\"soyad\":\"Deneme\","
                + "\"tcKimlikNo\":\"91000000002\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        long kayit = postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup
                + ",\"odemePlani\":\"DENEME\"}");
        String donem = YearMonth.now().toString();

        // Deneme dersi: DENEME kalir, tahakkuk/kredi yok; yoklama alinabilir
        mockMvc.perform(get("/api/students/{id}", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.status").value("DENEME"));
        long oturum = postId(t, "/api/attendance-sessions",
                "{\"grupId\":" + grup + ",\"tarih\":\"" + LocalDate.now() + "\"}");
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrenci + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + donem + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0))
                .andExpect(jsonPath("$.data.atlananDenemeOgrenciler.length()").value(1));
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data.length()").value(0));
        // Deneme ogrenciye "kredisi yok" bildirimi GITMEZ
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(jsonPath("$.data.bildirimler[?(@.tip == 'KREDI_BITTI')]").doesNotExist());

        // Plana gecir -> AKTIF + bu ayin kredisi; sonraki uretimde aidat
        mockMvc.perform(post("/api/enrollments/{id}/plana-gecir", kayit).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"odemePlani\":\"AYLIK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.odemePlani").value("AYLIK"))
                .andExpect(jsonPath("$.data.ogrenci.status").value("AKTIF"));
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + donem + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));
        // Ikinci kez plana gecirilemez
        mockMvc.perform(post("/api/enrollments/{id}/plana-gecir", kayit).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"odemePlani\":\"DONEMLIK\"}"))
                .andExpect(status().isBadRequest());
    }
}
