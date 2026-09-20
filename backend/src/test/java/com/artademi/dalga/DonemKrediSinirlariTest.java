package com.artademi.dalga;

import static org.hamcrest.Matchers.containsString;
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
 * Dönem/kredi bilinçli sınırları (§13.4 #3, ürün kararı 2026-09-20).
 *
 * <p>Kilitlenen kurallar: dönem ortasında kayıtta dönemlik ücret <b>kalan derse göre orantılanır</b>;
 * grup transferi <b>plana göre</b> para hesaplar, yeni gruba <b>kredi açar</b> ve yalnız dönemlik
 * ücreti olan gruptan transferde <b>patlamaz</b> (eskiden NPE → 500); elle tahakkuk ve elle paket
 * satışı öğrencinin <b>indirimini uygular</b>.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DonemKrediSinirlariTest {

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

    private static RequestPostProcessor admin(String t) {
        return jwt()
                .jwt(b -> b.subject("sub-admin").claim("tenant_id", t)
                        .claim("preferred_username", "test.admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long ogrenciAktif(String t, String tc) throws Exception {
        long id = postId(t, "/api/students", "{\"ad\":\"Ece\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        mockMvc.perform(patch("/api/students/{id}/status", id).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isOk());
        return id;
    }

    /**
     * Pzt+Çar dersli grup. {@code aylik}/{@code donemlik} null verilebilir (o ücret girilmemiş demek).
     * {@code donemId} null ise gruba dönem bağlanmaz.
     */
    private long grup(String t, String ad, Long donemId, String aylik, String donemlik) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        StringBuilder json = new StringBuilder("{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon);
        if (aylik != null) {
            json.append(",\"aylikAidat\":").append(aylik);
        }
        if (donemlik != null) {
            json.append(",\"donemlikUcret\":").append(donemlik);
        }
        if (donemId != null) {
            json.append(",\"donemId\":").append(donemId);
        }
        long grup = postId(t, "/api/groups", json.append("}").toString());
        postId(t, "/api/schedules", "{\"grupId\":" + grup
                + ",\"gun\":\"PAZARTESI\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");
        postId(t, "/api/schedules", "{\"grupId\":" + grup
                + ",\"gun\":\"CARSAMBA\",\"baslangicSaati\":\"12:00\",\"bitisSaati\":\"13:00\"}");
        return grup;
    }

    /** 14 Eyl – 11 Eki 2026, Pzt+Çar → toplam 8 ders. */
    private long donem(String t) throws Exception {
        return postId(t, "/api/donemler",
                "{\"ad\":\"Güz\",\"baslangic\":\"2026-09-14\",\"bitis\":\"2026-10-11\"}");
    }

    // =====================================================================
    // Dönem ortası orantılama
    // =====================================================================

    @Test
    void donemOrtasindaKayit_ucretKalanDerseGore_ORANTILANIR() throws Exception {
        String t = UUID.randomUUID().toString();
        long d = donem(t);
        long g = grup(t, "Bale", d, "2000.00", "8000.00");

        // Dönem başı: 8 dersin 8'i → TAM ücret (davranış değişmedi).
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", g).param("plan", "DONEMLIK")
                        .param("tarih", "2026-09-14").with(admin(t)))
                .andExpect(jsonPath("$.data.dersSayisi").value(8))
                .andExpect(jsonPath("$.data.donemToplamDers").value(8))
                .andExpect(jsonPath("$.data.tamUcret").value(8000.00))
                .andExpect(jsonPath("$.data.ucret").value(8000.00));

        // 28 Eyl'de kayıt: kalan 4 ders (28,30 Eyl + 5,7 Eki) → 8000 × 4/8 = 4000.
        mockMvc.perform(get("/api/groups/{id}/kayit-onizleme", g).param("plan", "DONEMLIK")
                        .param("tarih", "2026-09-28").with(admin(t)))
                .andExpect(jsonPath("$.data.dersSayisi").value(4))
                .andExpect(jsonPath("$.data.donemToplamDers").value(8))
                .andExpect(jsonPath("$.data.tamUcret").value(8000.00))
                .andExpect(jsonPath("$.data.ucret").value(4000.00));

        // Kayıt: paket de tahakkuk da orantılı tutarı taşır.
        long ogrenci = ogrenciAktif(t, "97100000001");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + g
                + ",\"kayitTarihi\":\"2026-09-28\",\"odemePlani\":\"DONEMLIK\"}");

        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toplamDers").value(4))
                .andExpect(jsonPath("$.data[0].tutar").value(4000.00));
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(4000.00));
    }

    // =====================================================================
    // Grup transferi
    // =====================================================================

    @Test
    void grupOlustururken_aylikAidat_ZORUNLUDUR() throws Exception {
        // Transferdeki .negate() NPE'si bu kural sayesinde API'den TETIKLENEMEZ: GRUP tipinde
        // aylikAidat zorunlu (GrupTutarli). Kolon nullable olduğu için kodda null koruması
        // yine de var; bu test kuralın kalkmasını fark ettirir.
        String t = UUID.randomUUID().toString();
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans X\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"X\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon X\"}");

        mockMvc.perform(post("/api/groups").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Yalniz donemlik\",\"tip\":\"GRUP\",\"bransId\":" + brans
                                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon
                                + ",\"donemlikUcret\":8000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.aylikAidat").exists());
    }

    @Test
    void donemlikTransfer_PATLAMAZ() throws Exception {
        String t = UUID.randomUUID().toString();
        long d = donem(t);
        long eski = grup(t, "Bale", d, "2000.00", "8000.00");
        long yeni = grup(t, "Modern", d, "2000.00", "6000.00");
        long ogrenci = ogrenciAktif(t, "97100000002");
        long kayit = postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + eski
                + ",\"kayitTarihi\":\"2026-09-14\",\"odemePlani\":\"DONEMLIK\"}");

        mockMvc.perform(post("/api/enrollments/{id}/transfer", kayit).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yeniGrupId\":" + yeni + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.odemePlani").value("DONEMLIK"));
    }

    @Test
    void donemlikTransfer_eskiGrubaIade_yeniGrubaKrediVeUcret() throws Exception {
        String t = UUID.randomUUID().toString();
        long d = donem(t);
        long eski = grup(t, "Bale", d, "2000.00", "8000.00");
        long yeni = grup(t, "Modern", d, "2000.00", "6000.00");
        long ogrenci = ogrenciAktif(t, "97100000003");
        long kayit = postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + eski
                + ",\"kayitTarihi\":\"2026-09-14\",\"odemePlani\":\"DONEMLIK\"}");

        mockMvc.perform(post("/api/enrollments/{id}/transfer", kayit).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yeniGrupId\":" + yeni + "}"))
                .andExpect(status().isOk());

        // Eski grubun kontörleri iptal (hayalet kredi kalmaz), yeni grupta AKTİF paket açıldı.
        String paketler = mockMvc.perform(get("/api/paketler")
                        .param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var kok = objectMapper.readTree(paketler).path("data");
        boolean eskiIptal = false;
        boolean yeniAktif = false;
        for (var p : kok) {
            long grupId = p.path("grupId").asLong();
            String durum = p.path("durum").asText();
            if (grupId == eski && "IPTAL".equals(durum)) {
                eskiIptal = true;
            }
            if (grupId == yeni && "AKTIF".equals(durum)) {
                yeniAktif = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(eskiIptal, "eski grubun kredisi iptal edilmeli");
        org.junit.jupiter.api.Assertions.assertTrue(yeniAktif, "yeni grupta kredi paketi açılmalı");

        // Yeni grubun ücreti de ORANTILI: 6000 × (kalan 6 / toplam 8) = 4500.
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data[?(@.grupId == " + yeni + ")].tutar", org.hamcrest.Matchers.contains(4500.00)));

        // Para: eski gruptan orantılı iade (negatif), yeni gruba orantılı ücret (pozitif).
        mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenci))
                        .param("size", "20").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.tutar < 0)]", hasSize(1)));
    }

    @Test
    void aylikTransfer_aidatFarkiKorunur_veYeniGrubaKrediAcilir() throws Exception {
        String t = UUID.randomUUID().toString();
        long eski = grup(t, "Bale", null, "2000.00", null);
        long yeni = grup(t, "Modern", null, "3000.00", null);
        long ogrenci = ogrenciAktif(t, "97100000004");
        long kayit = postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + eski
                + ",\"odemePlani\":\"AYLIK\"}");

        // Aylık planda kayıtta tahakkuk YOKTUR (Otomatik Tahakkuk keser); farkın çalışması için
        // eski gruba bu ayın tahakkukunu elle açıyoruz.
        String ay = java.time.YearMonth.now().toString();
        postId(t, "/api/accruals", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + eski
                + ",\"donem\":\"" + ay + "\",\"tutar\":2000.00,\"aciklama\":\"Aylık aidat\"}");

        mockMvc.perform(post("/api/enrollments/{id}/transfer", kayit).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yeniGrupId\":" + yeni + ",\"donem\":\"" + ay + "\"}"))
                .andExpect(status().isOk());

        // 2000 (elle) − 2000 (iade) + 3000 (geçiş) = 3000
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(3000.00));

        // Yeni grupta aylık kredi paketi açıldı — eskiden HİÇ açılmıyordu.
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data[?(@.grupId == " + yeni + " && @.durum == 'AKTIF')]", hasSize(1)));
    }

    // =====================================================================
    // Elle tahakkuk / elle paket satışı indirimi bilir
    // =====================================================================

    @Test
    void elleTahakkuk_ogrenciIndirimiUygulanir_brutIndirimNetDolar() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "97100000005");
        long indirim = postId(t, "/api/indirimler",
                "{\"ad\":\"Kardeş indirimi\",\"tip\":\"ORAN\",\"deger\":10}");
        postId(t, "/api/students/" + ogrenci + "/indirimler",
                "{\"indirimId\":" + indirim + ",\"baslangic\":\"2026-01-01\"}");

        mockMvc.perform(post("/api/accruals").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"donem\":\"2026-09\","
                                + "\"tutar\":1000.00,\"aciklama\":\"Kostüm bedeli\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tutar").value(900.00))
                .andExpect(jsonPath("$.data.brutTutar").value(1000.00))
                .andExpect(jsonPath("$.data.indirimTutar").value(100.00))
                .andExpect(jsonPath("$.data.indirimAciklama").value(containsString("Kardeş indirimi")));

        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(900.00));
    }

    @Test
    void elleTahakkuk_indirimsizOgrencide_tutarAYNEN_kalir() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "97100000006");
        mockMvc.perform(post("/api/accruals").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"donem\":\"2026-09\",\"tutar\":1000.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tutar").value(1000.00))
                .andExpect(jsonPath("$.data.indirimTutar").doesNotExist());
    }

    @Test
    void ellePaketSatisi_ogrenciIndirimiUygulanir() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "97100000007");
        long indirim = postId(t, "/api/indirimler", "{\"ad\":\"Burs\",\"tip\":\"TUTAR\",\"deger\":200}");
        postId(t, "/api/students/" + ogrenci + "/indirimler",
                "{\"indirimId\":" + indirim + ",\"baslangic\":\"2026-01-01\"}");

        mockMvc.perform(post("/api/paketler").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"ad\":\"10 ders\",\"toplamDers\":10,"
                                + "\"tutar\":2000.00,\"satisTarihi\":\"2026-09-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tutar").value(1800.00));

        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(1800.00));
    }
}
