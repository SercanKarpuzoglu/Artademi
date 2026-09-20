package com.artademi.dalga;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Iade (V37, urun karari 2026-09-20).
 *
 * <p>Kilitlenen kurallar: iade ORIJINAL satira dokunmadan NEGATIF satir yazar; bakiye, kasa ve
 * Gelirler ozeti (ucu de SUM) kendiliginden duzelir; iade ogrencinin KALAN KREDISINI iptal eder;
 * kismi iade siniri; iadenin iadesi yok; iadesi olan kayit silinemez; on buro iade yapamaz;
 * capraz-tenant iade yok. Urun iadesinde ayrica stok geri eklenir ve kasa satisi da gorur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IadeTest {

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
                .jwt(b -> b.subject("sub-" + rol).claim("tenant_id", t)
                        .claim("preferred_username", "test." + rol.toLowerCase())
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

    private long ogrenciAktif(String t, String tc) throws Exception {
        long id = postId(t, "/api/students", "{\"ad\":\"Ece\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        mockMvc.perform(patch("/api/students/{id}/status", id).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isOk());
        return id;
    }

    /** Donemli grup: donemlik 9000, haftada 2 ders. [grupId] doner; kayitta 8 kontorluk paket acilir. */
    private long donemliGrup(String t, String ad) throws Exception {
        long donem = postId(t, "/api/donemler",
                "{\"ad\":\"Güz\",\"baslangic\":\"2026-09-14\",\"bitis\":\"2026-10-11\"}");
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        long grup = postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":2000.00,"
                + "\"donemlikUcret\":9000.00,\"donemId\":" + donem + "}");
        postId(t, "/api/schedules", "{\"grupId\":" + grup + ",\"gun\":\"PAZARTESI\","
                + "\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");
        postId(t, "/api/schedules", "{\"grupId\":" + grup + ",\"gun\":\"CARSAMBA\","
                + "\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");
        return grup;
    }

    private long kasa(String t, String ad) throws Exception {
        return postId(t, "/api/kasalar",
                "{\"ad\":\"" + ad + "\",\"tip\":\"NAKIT\",\"acilisBakiyesi\":0.00}");
    }

    private double kasaBakiyesi(String t, long kasaId) throws Exception {
        String body = mockMvc.perform(get("/api/kasalar/{id}", kasaId).with(admin(t)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("bakiye").asDouble();
    }

    // =====================================================================
    // Tahsilat iadesi
    // =====================================================================

    @Test
    void odemeIadesi_negatifSatirYazar_bakiyeKasaGelirDuzelir_krediIptalOlur() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = donemliGrup(t, "Bale");
        long ogrenci = ogrenciAktif(t, "98100000001");
        long kasaId = kasa(t, "Merkez Kasa");

        // Donemlik kayit: 9000 TL tahakkuk + 8 kontorluk paket.
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup
                + ",\"kayitTarihi\":\"2026-09-14\",\"odemePlani\":\"DONEMLIK\"}");
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(9000.00));

        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup
                + ",\"tutar\":9000.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\","
                + "\"kasaId\":" + kasaId + "}");
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(0.00));
        org.junit.jupiter.api.Assertions.assertEquals(9000.00, kasaBakiyesi(t, kasaId), 0.001);

        // Onizleme: tamami iade edilebilir, 8 kontor gidecek.
        mockMvc.perform(get("/api/payments/{id}/iade-onizleme", odeme).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.odenenTutar").value(9000.00))
                .andExpect(jsonPath("$.data.iadeEdilenTutar").value(0.00))
                .andExpect(jsonPath("$.data.iadeEdilebilir").value(9000.00))
                .andExpect(jsonPath("$.data.iptalEdilecekPaket").value(1))
                .andExpect(jsonPath("$.data.iptalEdilecekKontor").value(8))
                .andExpect(jsonPath("$.data.engel").doesNotExist());

        // Iade: ORIJINAL satir durur, yeni satir NEGATIF.
        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutar\":9000.00,\"iadeTarihi\":\"2026-09-20\",\"aciklama\":\"Veli ayrıldı\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tutar").value(-9000.00))
                .andExpect(jsonPath("$.data.iadeEdilenOdemeId").value((int) odeme));
        mockMvc.perform(get("/api/payments/{id}", odeme).with(admin(t)))
                .andExpect(jsonPath("$.data.tutar").value(9000.00));

        // Bakiye yeniden BORC, kasadan para cikti, Gelirler sifirlandi — hicbiri ayrica hesaplanmadi.
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(jsonPath("$.data.bakiye").value(9000.00));
        org.junit.jupiter.api.Assertions.assertEquals(0.00, kasaBakiyesi(t, kasaId), 0.001);
        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-01")
                        .param("to", "2026-09-30").with(admin(t)))
                .andExpect(jsonPath("$.data.odemeToplam").value(0.00));

        // Kalan kredi IPTAL: parayi geri verip kontorleri birakmak bedava ders vermektir.
        mockMvc.perform(get("/api/paketler").param("ogrenciId", String.valueOf(ogrenci)).with(admin(t)))
                .andExpect(jsonPath("$.data[0].durum").value("IPTAL"));
    }

    @Test
    void kismiIade_sinirAsimi_400_alanBazliHata() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "98100000002");
        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":500.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\"}");

        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":300.00}"))
                .andExpect(status().isCreated());

        // Kalan 200; 300 daha iade edilemez.
        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":300.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.tutar").exists());

        mockMvc.perform(get("/api/payments/{id}/iade-onizleme", odeme).with(admin(t)))
                .andExpect(jsonPath("$.data.iadeEdilenTutar").value(300.00))
                .andExpect(jsonPath("$.data.iadeEdilebilir").value(200.00));

        // Kalan 200 iade edilebilir; sonrasinda uc TUKENIR.
        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":200.00}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/payments/{id}/iade-onizleme", odeme).with(admin(t)))
                .andExpect(jsonPath("$.data.engel").value("Bu tahsilatın tamamı zaten iade edilmiş."));
    }

    @Test
    void iadeninIadesi_yapilamaz_400() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "98100000003");
        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":500.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\"}");
        long iade = postId(t, "/api/payments/" + odeme + "/iade", "{\"tutar\":500.00}");

        mockMvc.perform(post("/api/payments/{id}/iade", iade).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Bu kayıt zaten bir iade; iadenin iadesi yapılamaz."));
    }

    @Test
    void iadesiOlanTahsilat_SILINEMEZ() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "98100000004");
        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":500.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\"}");
        postId(t, "/api/payments/" + odeme + "/iade", "{\"tutar\":500.00}");

        // Silinseydi iade satiri sahipsiz kalirdi: "geri verilen para" durur, neyin iadesi belirsiz.
        mockMvc.perform(get("/api/silme/{tur}/{id}/onizleme", "odeme", odeme).with(admin(t)))
                .andExpect(jsonPath("$.data.silinebilir").value(false));
        mockMvc.perform(delete("/api/silme/{tur}/{id}", "odeme", odeme).with(admin(t)))
                .andExpect(status().isConflict());
    }

    @Test
    void iade_onBuroYapamaz_403_muhasebeYapabilir() throws Exception {
        String t = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(t, "98100000005");
        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":500.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\"}");

        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(rol(t, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":100.00}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(rol(t, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":100.00}"))
                .andExpect(status().isCreated());
    }

    @Test
    void baskaTenantinTahsilati_iadeEdilemez_404() throws Exception {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        long ogrenci = ogrenciAktif(a, "98100000006");
        long odeme = postId(a, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":500.00,\"odemeTarihi\":\"2026-09-15\",\"odemeYontemi\":\"NAKIT\"}");

        mockMvc.perform(post("/api/payments/{id}/iade", odeme).with(admin(b))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tutar\":100.00}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/{id}/iade-onizleme", odeme).with(admin(b)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // Urun iadesi
    // =====================================================================

    @Test
    void urunIadesi_stokGeriGelir_negatifSatir_kasaVeGelirDuser() throws Exception {
        String t = UUID.randomUUID().toString();
        long kasaId = kasa(t, "Merkez Kasa");
        long urun = postId(t, "/api/products", "{\"ad\":\"Mayo\",\"satisFiyati\":300.00,\"stokAdedi\":10}");
        long ogrenci = ogrenciAktif(t, "98100000007");

        long satis = postId(t, "/api/sales", "{\"urunId\":" + urun + ",\"ogrenciId\":" + ogrenci
                + ",\"adet\":3,\"satisTarihi\":\"2026-09-15\",\"kasaId\":" + kasaId + "}");
        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t)))
                .andExpect(jsonPath("$.data.stokAdedi").value(7));
        // V37: satis artik KASAYA da isleniyor (once hicbir kasaya girmiyordu).
        org.junit.jupiter.api.Assertions.assertEquals(900.00, kasaBakiyesi(t, kasaId), 0.001);

        mockMvc.perform(get("/api/sales/{id}/iade-onizleme", satis).with(admin(t)))
                .andExpect(jsonPath("$.data.satilanAdet").value(3))
                .andExpect(jsonPath("$.data.iadeEdilebilir").value(3))
                .andExpect(jsonPath("$.data.birimFiyat").value(300.00));

        // Kismi iade: 3 adetten 1'i. Silmeyle bu yapilamazdi.
        mockMvc.perform(post("/api/sales/{id}/iade", satis).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adet\":1,\"iadeTarihi\":\"2026-09-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adet").value(-1))
                .andExpect(jsonPath("$.data.toplamTutar").value(-300.00))
                .andExpect(jsonPath("$.data.iadeEdilenSatisId").value((int) satis));

        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t)))
                .andExpect(jsonPath("$.data.stokAdedi").value(8));
        org.junit.jupiter.api.Assertions.assertEquals(600.00, kasaBakiyesi(t, kasaId), 0.001);
        mockMvc.perform(get("/api/finance/gelir-ozeti").param("from", "2026-09-01")
                        .param("to", "2026-09-30").with(admin(t)))
                .andExpect(jsonPath("$.data.satisToplam").value(600.00));

        // Kalan 2; 3 iade edilemez.
        mockMvc.perform(post("/api/sales/{id}/iade", satis).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adet\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.adet").exists());
    }

    @Test
    void urunIadesi_fiyatDegisse_bile_SATIN_ALINAN_fiyattanDoner() throws Exception {
        String t = UUID.randomUUID().toString();
        long urun = postId(t, "/api/products", "{\"ad\":\"Mayo\",\"satisFiyati\":300.00,\"stokAdedi\":10}");
        long satis = postId(t, "/api/sales",
                "{\"urunId\":" + urun + ",\"adet\":1,\"satisTarihi\":\"2026-09-15\"}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/products/{id}", urun).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Mayo\",\"satisFiyati\":900.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sales/{id}/iade", satis).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"adet\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.toplamTutar").value(-300.00));
    }

    @Test
    void iadesiOlanSatis_SILINEMEZ() throws Exception {
        String t = UUID.randomUUID().toString();
        long urun = postId(t, "/api/products", "{\"ad\":\"Mayo\",\"satisFiyati\":300.00,\"stokAdedi\":10}");
        long satis = postId(t, "/api/sales",
                "{\"urunId\":" + urun + ",\"adet\":2,\"satisTarihi\":\"2026-09-15\"}");
        postId(t, "/api/sales/" + satis + "/iade", "{\"adet\":1}");

        mockMvc.perform(delete("/api/silme/{tur}/{id}", "satis", satis).with(admin(t)))
                .andExpect(status().isConflict());
    }
}
