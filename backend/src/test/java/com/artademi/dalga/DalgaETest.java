package com.artademi.dalga;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
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
 * Dalga E: donem tanimi, grup donem/donemlik ucret, kayitta plan secimi (onizleme), donemlik kredi + tek
 * tahakkuk, aylik kredi (kayitta ve Otomatik Tahakkuk'ta), donem disi derse gelen icin ofise uyari.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaETest {

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
                .jwt(b -> b.subject("sub-" + rol).claim("tenant_id", t).claim("preferred_username", "test." + rol.toLowerCase())
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

    /** brans + salon + egitmen + grup (aylik 2000, donemlik 9000, donemId opsiyonel); [grup] doner. */
    private long grup(String t, String ad, Long donemId) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        return postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":2000.00,"
                + "\"donemlikUcret\":9000.00" + (donemId == null ? "" : ",\"donemId\":" + donemId) + "}");
    }

    private long ogrenciAktif(String t, String ad, String tc) throws Exception {
        long id = postId(t, "/api/students", "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        mockMvc.perform(patch("/api/students/{id}/status", id).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}")).andExpect(status().isOk());
        return id;
    }

    private void ders(String t, long grup, String gun, String bas, String bit) throws Exception {
        postId(t, "/api/schedules", "{\"grupId\":" + grup + ",\"gun\":\"" + gun + "\",\"baslangicSaati\":\"" + bas
                + "\",\"bitisSaati\":\"" + bit + "\"}");
    }

    private void yoklama(String t, long grup, long ogrenci, LocalDate tarih, String durum) throws Exception {
        long oturum = postId(t, "/api/attendance-sessions", "{\"grupId\":" + grup + ",\"tarih\":\"" + tarih + "\"}");
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturum).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrenci + ",\"durum\":\"" + durum + "\"}]"))
                .andExpect(status().isOk());
    }

    @Test
    void donemlikKayit_onizleme_kredi_tekTahakkuk_aidatUretilmez_donemDisiUyari() throws Exception {
        String t = UUID.randomUUID().toString();
        long donem = postId(t, "/api/donemler", "{\"ad\":\"Güz\",\"baslangic\":\"2026-09-14\",\"bitis\":\"2026-10-11\"}");
        long grup = grup(t, "Bale", donem);
        ders(t, grup, "PAZARTESI", "10:00", "11:00");
        ders(t, grup, "CARSAMBA", "10:00", "11:00");
        mockMvc.perform(get("/api/groups/{id}", grup).with(admin(t)))
                .andExpect(jsonPath("$.data.donem.ad").value("Güz"))
                .andExpect(jsonPath("$.data.donemlikUcret").value(9000.00));

        // Onizleme: 14 Eyl - 11 Eki, Pzt+Car -> 8 ders, 9000; aylik: 14-30 Eyl -> 6 ders, 2000
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", grup).param("plan", "DONEMLIK").param("tarih", "2026-09-14").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uygun").value(true))
                .andExpect(jsonPath("$.data.donemAd").value("Güz"))
                .andExpect(jsonPath("$.data.haftalikDers").value(2))
                .andExpect(jsonPath("$.data.dersSayisi").value(8))
                .andExpect(jsonPath("$.data.ucret").value(9000.00));
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", grup).param("plan", "AYLIK").param("tarih", "2026-09-14").with(admin(t)))
                .andExpect(jsonPath("$.data.dersSayisi").value(6))
                .andExpect(jsonPath("$.data.ucret").value(2000.00));

        long ogrenci = ogrenciAktif(t, "Ece", "98000000001");
        mockMvc.perform(post("/api/enrollments").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + ",\"kayitTarihi\":\"2026-09-14\",\"odemePlani\":\"DONEMLIK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.odemePlani").value("DONEMLIK"))
                .andExpect(jsonPath("$.data.donem.ad").value("Güz"));

        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toplamDers").value(8))
                .andExpect(jsonPath("$.data[0].kalanDers").value(8))
                .andExpect(jsonPath("$.data[0].tutar").value(9000.00))
                .andExpect(jsonPath("$.data[0].sonKullanmaTarihi").value("2026-10-11"));
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tutar").value(9000.00))
                .andExpect(jsonPath("$.data[0].aciklama").value(containsString("Dönemlik ücret")));

        // Aylik aidat uretimi donemlik ogrenciyi ATLAR
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"2026-09\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0));
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Derse geldi -> kredi duser (8 -> 7)
        yoklama(t, grup, ogrenci, LocalDate.of(2026, 9, 14), "GELDI");
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data[0].kalanDers").value(7));

        // Donem bittikten sonra derse geldi -> ofise KREDI_BITTI
        yoklama(t, grup, ogrenci, LocalDate.of(2026, 10, 14), "GELDI");
        mockMvc.perform(get("/api/bildirimler").with(admin(t)))
                .andExpect(jsonPath("$.data.bildirimler[?(@.tip == 'KREDI_BITTI')].baslik").value(hasItem(containsString("Ece Test"))));
    }

    @Test
    void aylikKayit_kayittaAyKredisi_otomatikTahakkuktaSonrakiAy() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = grup(t, "Piyano", null);
        ders(t, grup, "SALI", "10:00", "11:00");
        YearMonth buAy = YearMonth.now();
        LocalDate ayBasi = buAy.atDay(1);
        int saliSayisi = 0;
        for (LocalDate d = ayBasi; !d.isAfter(buAy.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.TUESDAY) {
                saliSayisi++;
            }
        }
        long ogrenci = ogrenciAktif(t, "Can", "98000000002");
        mockMvc.perform(post("/api/enrollments").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + ",\"kayitTarihi\":\"" + ayBasi + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.odemePlani").value("AYLIK"));

        // Kayitta bu ayin kredisi (0 TL), tahakkuk yok
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toplamDers").value(saliSayisi))
                .andExpect(jsonPath("$.data[0].tutar").value(0.00))
                .andExpect(jsonPath("$.data[0].accrualId").doesNotExist());
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Otomatik Tahakkuk: aidat 2000 + (bu ay kredi zaten var -> mukerrer yok)
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + buAy + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tutar").value(2000.00));
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Sonraki ay: aidat + o ayin kredisi
        mockMvc.perform(post("/api/accruals/uret").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donem\":\"" + buAy.plusMonths(1) + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void dogrulama_yetki_silmeEngeli() throws Exception {
        String t = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/donemler").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ters\",\"baslangic\":\"2026-10-01\",\"bitis\":\"2026-09-01\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/donemler").with(rol(t, "FRONTDESK")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"X\",\"baslangic\":\"2026-09-01\",\"bitis\":\"2026-10-01\"}"))
                .andExpect(status().isForbidden());
        long donem = postId(t, "/api/donemler", "{\"ad\":\"Bahar\",\"baslangic\":\"2027-02-01\",\"bitis\":\"2027-06-15\"}");
        mockMvc.perform(get("/api/donemler").with(rol(t, "FRONTDESK"))).andExpect(jsonPath("$.data", hasSize(1)));

        long donemsizGrup = grup(t, "Gitar", null);
        long ogrenci = ogrenciAktif(t, "Ali", "98000000003");
        // Donemi olmayan gruba donemlik kayit -> 400
        mockMvc.perform(post("/api/enrollments").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + donemsizGrup + ",\"odemePlani\":\"DONEMLIK\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", donemsizGrup).param("plan", "DONEMLIK").with(admin(t)))
                .andExpect(jsonPath("$.data.uygun").value(false))
                .andExpect(jsonPath("$.data.neden").value(containsString("dönemi tanımlı değil")));
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", donemsizGrup).param("plan", "AYLIK").with(rol(t, "TEACHER")))
                .andExpect(status().isForbidden());

        // Donem gruba bagliysa silinemez
        long donemliGrup = grup(t, "Keman", donem);
        mockMvc.perform(delete("/api/silme/donem/{id}", donem).with(admin(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("1 grup bu döneme bağlı")));
        mockMvc.perform(get("/api/groups/{id}", donemliGrup).with(admin(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }
}
