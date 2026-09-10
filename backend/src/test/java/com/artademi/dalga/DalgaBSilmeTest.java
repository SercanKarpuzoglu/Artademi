package com.artademi.dalga;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Dalga B-3 yumusak silme: gizlenme, bire-bir referanslarin yuklenmeye devam etmesi (odeme listesi silinmis
 * ogrencinin adini gosterir), engel kurallari, satis/stok, geri alma, yetki.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DalgaBSilmeTest {

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
                .jwt(b -> b.claim("tenant_id", t).claim("preferred_username", "test.admin")
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

    private long[] grupKur(String t, String ad) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans " + ad + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca\",\"soyad\":\"" + ad + "\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon " + ad + "\"}");
        long grup = postId(t, "/api/groups", "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}");
        return new long[] {grup, ogretmen, salon, brans};
    }

    @Test
    void ogrenciSil_gizlenir_odemeListesiAdiGosterir_kayitAyrilir_geriAlinir() throws Exception {
        String t = UUID.randomUUID().toString();
        long grup = grupKur(t, "Bale")[0];
        long ogrenci = postId(t, "/api/students", "{\"ad\":\"Sil\",\"soyad\":\"Test\",\"tcKimlikNo\":\"95000000001\","
                + "\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long odeme = postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci + ",\"tutar\":300.00,\"odemeTarihi\":\""
                + LocalDate.now() + "\",\"odemeYontemi\":\"NAKIT\"}");

        // Onizleme: silinebilir, etkiler ve bagli kayitlar
        mockMvc.perform(get("/api/silme/ogrenci/{id}/onizleme", ogrenci).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.silinebilir").value(true))
                .andExpect(jsonPath("$.data.ad").value("Sil Test"))
                .andExpect(jsonPath("$.data.etkiler[0]").value("1 aktif grup kaydı AYRILDI olacak"))
                .andExpect(jsonPath("$.data.bagliKayitlar[?(@.ad == 'Ödeme')].sayi").value(1));

        mockMvc.perform(delete("/api/silme/ogrenci/{id}", ogrenci).with(admin(t)))
                .andExpect(status().isOk());

        // Gizlendi: detay 404, listelerde yok
        mockMvc.perform(get("/api/students/{id}", ogrenci).with(admin(t))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/students").with(admin(t)))
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").doesNotExist());
        mockMvc.perform(get("/api/students/liste").with(admin(t)))
                .andExpect(jsonPath("$.data[?(@.id == " + ogrenci + ")]").doesNotExist());

        // Aktif kayit AYRILDI oldu
        mockMvc.perform(get("/api/groups/{id}/enrollments", grup).param("durum", "AKTIF").with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // KRITIK: para izi — odeme listesi acilir ve silinmis ogrencinin adi gorunur (bire-bir yukleme)
        mockMvc.perform(get("/api/payments").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + odeme + ")].ogrenci.ad").value("Sil"));

        // Silinenler listesi + geri al
        mockMvc.perform(get("/api/silme/silinenler").param("tur", "ogrenci").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ogrenci))
                .andExpect(jsonPath("$.data[0].ad").value("Sil Test"))
                .andExpect(jsonPath("$.data[0].silen").value("test.admin"));
        mockMvc.perform(post("/api/silme/ogrenci/{id}/geri-al", ogrenci).with(admin(t)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/students/{id}", ogrenci).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ad").value("Sil"));
        mockMvc.perform(get("/api/silme/silinenler").param("tur", "ogrenci").with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Baska tenant silemez / geri alamaz
        mockMvc.perform(delete("/api/silme/ogrenci/{id}", ogrenci).with(admin(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void egitmen_grubuVarken_silinemez_grupSilinince_silinir() throws Exception {
        String t = UUID.randomUUID().toString();
        long[] r = grupKur(t, "Piyano");
        long grup = r[0];
        long ogretmen = r[1];
        long salon = r[2];

        mockMvc.perform(get("/api/silme/egitmen/{id}/onizleme", ogretmen).with(admin(t)))
                .andExpect(jsonPath("$.data.silinebilir").value(false))
                .andExpect(jsonPath("$.data.engel").value("1 grup bu eğitmene atanmış; önce grupların eğitmenini değiştirin"));
        mockMvc.perform(delete("/api/silme/egitmen/{id}", ogretmen).with(admin(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SILINEMEZ"));
        mockMvc.perform(delete("/api/silme/salon/{id}", salon).with(admin(t)))
                .andExpect(status().isConflict());

        // Grubu sil (ders saati de gizlenir), sonra egitmen ve salon serbest
        postId(t, "/api/schedules", "{\"grupId\":" + grup
                + ",\"gun\":\"CUMA\",\"baslangicSaati\":\"10:00\",\"bitisSaati\":\"11:00\"}");
        mockMvc.perform(get("/api/silme/grup/{id}/onizleme", grup).with(admin(t)))
                .andExpect(jsonPath("$.data.etkiler[0]").value("1 ders saati silinecek"));
        mockMvc.perform(delete("/api/silme/grup/{id}", grup).with(admin(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/groups/{id}", grup).with(admin(t))).andExpect(status().isNotFound());
        // Grup gizlenince ders saati ucu da grubu bulamaz (404) — ders saatleri de damgalandi.
        mockMvc.perform(get("/api/groups/{id}/schedules", grup).with(admin(t)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/schedules").param("grupId", String.valueOf(grup)).with(admin(t)))
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(delete("/api/silme/egitmen/{id}", ogretmen).with(admin(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/teachers/{id}", ogretmen).with(admin(t))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/silme/salon/{id}", salon).with(admin(t))).andExpect(status().isOk());
    }

    @Test
    void satis_silinince_stokGeriGelir_geriAlinca_duser() throws Exception {
        String t = UUID.randomUUID().toString();
        long urun = postId(t, "/api/products", "{\"ad\":\"Kitap\",\"satisFiyati\":50.00,\"stokAdedi\":10}");
        long satis = postId(t, "/api/sales", "{\"urunId\":" + urun + ",\"adet\":2,\"satisTarihi\":\"2026-09-01\"}");
        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t))).andExpect(jsonPath("$.data.stokAdedi").value(8));

        mockMvc.perform(delete("/api/silme/satis/{id}", satis).with(admin(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t))).andExpect(jsonPath("$.data.stokAdedi").value(10));
        mockMvc.perform(get("/api/sales").with(admin(t))).andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(post("/api/silme/satis/{id}/geri-al", satis).with(admin(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", urun).with(admin(t))).andExpect(jsonPath("$.data.stokAdedi").value(8));
        mockMvc.perform(get("/api/sales").with(admin(t))).andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void yetki_yalnizAdmin_bilinmeyenTur404() throws Exception {
        String t = UUID.randomUUID().toString();
        long urun = postId(t, "/api/products", "{\"ad\":\"X\",\"satisFiyati\":5.00}");
        mockMvc.perform(delete("/api/silme/urun/{id}", urun).with(rol(t, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/silme/urun/{id}/onizleme", urun).with(rol(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/silme/bilinmeyen/{id}", urun).with(admin(t)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/silme/urun/{id}", 999999).with(admin(t)))
                .andExpect(status().isNotFound());
    }
}
