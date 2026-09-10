package com.artademi.dalga;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Dalga D: indirim tanimi + ogrenciye ozel atama + otomatik tahakkukta brut - indirim = net; tarih araligi,
 * grup ozelligi, pasif tanim, silme engeli, yetki ve tenant izolasyonu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaDTest {

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
                .jwt(b -> b.claim("tenant_id", t).claim("preferred_username", "test." + rol.toLowerCase())
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

    /** AKTIF ogrenci + aidat 500 grup kaydi; [grup, ogrenci] doner. */
    private long[] kurulum(String t, String ad, String tc) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        long grup = postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}");
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Ece\",\"soyad\":\"" + ad + "\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        mockMvc.perform(patch("/api/students/{id}/status", ogrenci).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}")).andExpect(status().isOk());
        return new long[] {grup, ogrenci};
    }

    private void uret(String t, String donem) throws Exception {
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"donem\":\"" + donem + "\"}")).andExpect(status().isOk());
    }

    @Test
    void oranVeTutarIndirimi_tahakkukta_brutEksiIndirimNet() throws Exception {
        String t = UUID.randomUUID().toString();
        long[] k = kurulum(t, "Bale", "97000000001");
        long kardes = postId(t, "/api/indirimler", "{\"ad\":\"Kardeş indirimi\",\"tip\":\"ORAN\",\"deger\":10}");
        long burs = postId(t, "/api/indirimler", "{\"ad\":\"Burs\",\"tip\":\"TUTAR\",\"deger\":50}");
        String buAy = YearMonth.now().toString();
        LocalDate ayBasi = YearMonth.now().atDay(1);

        // Tum gruplara %10 + yalniz bu gruba 50 TL (donem basindan gecerli)
        postId(t, "/api/students/" + k[1] + "/indirimler", "{\"indirimId\":" + kardes + ",\"baslangic\":\"" + ayBasi + "\"}");
        postId(t, "/api/students/" + k[1] + "/indirimler", "{\"indirimId\":" + burs + ",\"grupId\":" + k[0]
                + ",\"baslangic\":\"" + ayBasi + "\",\"aciklama\":\"Yarı burs\"}");

        mockMvc.perform(get("/api/students/{id}/indirimler", k[1]).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].indirim.etiket").value("50 ₺"))
                .andExpect(jsonPath("$.data[0].grupAd").value("Bale"))
                .andExpect(jsonPath("$.data[1].indirim.etiket").value("%10"))
                .andExpect(jsonPath("$.data[1].grupAd").doesNotExist());

        // Onizleme: 500 - (50 + 50) = 400
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", buAy).with(admin(t)))
                .andExpect(jsonPath("$.data.ozet[0].brut").value(500.00))
                .andExpect(jsonPath("$.data.ozet[0].indirim").value(100.00))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(400.00))
                .andExpect(jsonPath("$.data.ozet[0].indirimAciklama").value(containsString("Kardeş indirimi %10")))
                .andExpect(jsonPath("$.data.toplamTutar").value(400.00));

        uret(t, buAy);
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(k[1])).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tutar").value(400.00))
                .andExpect(jsonPath("$.data[0].brutTutar").value(500.00))
                .andExpect(jsonPath("$.data[0].indirimTutar").value(100.00))
                .andExpect(jsonPath("$.data[0].indirimAciklama").value(containsString("Burs 50 ₺")))
                .andExpect(jsonPath("$.data[0].aciklama").value(containsString("indirim")));
        mockMvc.perform(get("/api/students/{id}/balance", k[1]).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(400.00));

        // Grup-ozel 50 TL'yi bitir -> sonraki ay yalniz %10: 450
        String body = mockMvc.perform(get("/api/students/{id}/indirimler", k[1]).with(admin(t)))
                .andReturn().getResponse().getContentAsString();
        long bursAtama = objectMapper.readTree(body).path("data").get(0).path("id").asLong();
        mockMvc.perform(patch("/api/ogrenci-indirimleri/{id}/bitir", bursAtama).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));
        String sonrakiAy = YearMonth.now().plusMonths(1).toString();
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", sonrakiAy).with(admin(t)))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(450.00))
                .andExpect(jsonPath("$.data.ozet[0].indirim").value(50.00));

        // Tanim pasif -> indirim yok
        mockMvc.perform(patch("/api/indirimler/{id}/durum", kardes).param("aktif", "false").with(admin(t)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", sonrakiAy).with(admin(t)))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(500.00))
                .andExpect(jsonPath("$.data.ozet[0].indirim").doesNotExist());
    }

    @Test
    void tarihAraligi_disinda_uygulanmaz_indirimBrutuAsamaz() throws Exception {
        String t = UUID.randomUUID().toString();
        long[] k = kurulum(t, "Piyano", "97000000002");
        long gecmis = postId(t, "/api/indirimler", "{\"ad\":\"Eski kampanya\",\"tip\":\"ORAN\",\"deger\":50}");
        long buyuk = postId(t, "/api/indirimler", "{\"ad\":\"Tam burs\",\"tip\":\"TUTAR\",\"deger\":900}");
        String buAy = YearMonth.now().toString();
        LocalDate ayBasi = YearMonth.now().atDay(1);

        // Gecen ay bitmis atama -> bu ay uygulanmaz
        postId(t, "/api/students/" + k[1] + "/indirimler", "{\"indirimId\":" + gecmis + ",\"baslangic\":\""
                + ayBasi.minusMonths(2) + "\",\"bitis\":\"" + ayBasi.minusDays(1) + "\"}");
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", buAy).with(admin(t)))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(500.00));

        // 900 TL burs 500'u asamaz -> net 0
        postId(t, "/api/students/" + k[1] + "/indirimler", "{\"indirimId\":" + buyuk + ",\"baslangic\":\"" + ayBasi + "\"}");
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", buAy).with(admin(t)))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(0.00))
                .andExpect(jsonPath("$.data.ozet[0].indirim").value(500.00));
    }

    @Test
    void dogrulama_yetki_silmeEngeli_tenantIzolasyonu() throws Exception {
        String t = UUID.randomUUID().toString();
        long[] k = kurulum(t, "Gitar", "97000000003");
        mockMvc.perform(post("/api/indirimler").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Fazla\",\"tip\":\"ORAN\",\"deger\":150}"))
                .andExpect(status().isBadRequest());
        long tanim = postId(t, "/api/indirimler", "{\"ad\":\"Nakit\",\"tip\":\"ORAN\",\"deger\":5}");

        // Muhasebe okur ve atar, tanim yazamaz; on buro hicbirini goremez
        mockMvc.perform(get("/api/indirimler").with(rol(t, "FRONTDESK_ACCOUNTING"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/indirimler").with(rol(t, "FRONTDESK_ACCOUNTING")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"X\",\"tip\":\"ORAN\",\"deger\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/indirimler").with(rol(t, "FRONTDESK"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/students/" + k[1] + "/indirimler").with(rol(t, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"indirimId\":" + tanim + "}"))
                .andExpect(status().isCreated());

        // Aktif atamasi olan tanim silinemez
        mockMvc.perform(delete("/api/silme/indirim/{id}", tanim).with(admin(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SILINEMEZ"));

        // Baska tenant: tanim gorunmez, atama 404
        String t2 = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/indirimler").with(admin(t2))).andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(post("/api/students/" + k[1] + "/indirimler").with(admin(t2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"indirimId\":" + tanim + "}"))
                .andExpect(status().isNotFound());
    }
}
