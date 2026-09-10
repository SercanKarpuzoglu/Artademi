package com.artademi.dalga;

import static org.hamcrest.Matchers.containsString;
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
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantStatus;

/**
 * 10 Eylul kapanis eksikleri: (1) kara listedeki TC ile YENI kayit acilarak liste atlanamaz
 * (basvuru donusturmesi dahil), (2) bransa varsayilan donem ve donem silme engeli.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EksiklerTest {

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
    com.artademi.platform.TenantRepository tenantRepo;

    private static RequestPostProcessor admin(String t) {
        return jwt()
                .jwt(b -> b.claim("tenant_id", t).claim("preferred_username", "test.admin")
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

    @Test
    void karaListedekiTcIle_yeniKayit_uyarir_onaylaninca_acilir() throws Exception {
        String t = UUID.randomUUID().toString();
        String tc = "97700000001";
        long eski = postId(t, "/api/students", "{\"ad\":\"Can\",\"soyad\":\"Eski\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        mockMvc.perform(patch("/api/students/{id}/kara-liste", eski).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"karaListe\":true,\"aciklama\":\"Ödeme yapmadan ayrıldı\"}"))
                .andExpect(status().isOk());

        // Ayni TC ile YENI kayit -> 409 KARA_LISTE, sebep mesajda (liste atlanamaz)
        String yeniJson = "{\"ad\":\"Can\",\"soyad\":\"Yeni\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}";
        mockMvc.perform(post("/api/students").with(admin(t)).contentType(MediaType.APPLICATION_JSON).content(yeniJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KARA_LISTE"))
                .andExpect(jsonPath("$.error.message").value(containsString("Ödeme yapmadan ayrıldı")));

        // Onayla -> 201
        mockMvc.perform(post("/api/students").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content(yeniJson.substring(0, yeniJson.length() - 1) + ",\"karaListeOnayi\":true}"))
                .andExpect(status().isCreated());

        // Kara listeden cikinca uyari da biter
        mockMvc.perform(patch("/api/students/{id}/kara-liste", eski).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"karaListe\":false}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/students").with(admin(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Can\",\"soyad\":\"Ucuncu\",\"tcKimlikNo\":\"" + tc
                                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void basvuruDonusturme_karaListedekiTc_uyarir_onaylaninca_donusur() throws Exception {
        // Public basvuru slug'a bagli; slug icin AKTIF tenant kaydi gerekir (BasvuruYonetimTest ile ayni yaklasim).
        String slug = "kurum-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant kurum = Tenant.create("Kurum " + UUID.randomUUID());
        kurum.setStatus(TenantStatus.AKTIF);
        kurum.setBasvuruSlug(slug);
        String t = tenantRepo.save(kurum).getId().toString();
        String tc = "97700000002";
        long eski = postId(t, "/api/students", "{\"ad\":\"Ela\",\"soyad\":\"Eski\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}");
        mockMvc.perform(patch("/api/students/{id}/kara-liste", eski).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"karaListe\":true,\"aciklama\":\"Devamsız\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/basvuru/{slug}", slug)
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ela\",\"soyad\":\"Yeni\",\"telefon\":\"05551112233\"}"))
                .andExpect(status().isOk());
        String liste = mockMvc.perform(get("/api/basvurular").with(admin(t))).andReturn().getResponse().getContentAsString();
        long basvuruId = objectMapper.readTree(liste).path("data").get(0).path("id").asLong();

        String donusturJson = "{\"tcKimlikNo\":\"" + tc + "\",\"dogumTarihi\":\"2012-01-01\",\"yetiskinMi\":true}";
        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", basvuruId).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(donusturJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KARA_LISTE"))
                .andExpect(jsonPath("$.error.message").value(containsString("Devamsız")));
        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", basvuruId).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(donusturJson.substring(0, donusturJson.length() - 1) + ",\"karaListeOnayi\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void bransaDonem_atanir_donemSilmeEngeli_bransiDaSayar() throws Exception {
        String t = UUID.randomUUID().toString();
        long donem = postId(t, "/api/donemler", "{\"ad\":\"Güz\",\"baslangic\":\"2026-09-14\",\"bitis\":\"2027-01-31\"}");
        long brans = postId(t, "/api/branches", "{\"ad\":\"Piyano\",\"donemId\":" + donem + "}");
        mockMvc.perform(get("/api/branches/{id}", brans).with(admin(t)))
                .andExpect(jsonPath("$.data.donem.id").value(donem))
                .andExpect(jsonPath("$.data.donem.ad").value("Güz"));

        // Donem yalnizca bransa bagliyken bile silinemez
        mockMvc.perform(delete("/api/silme/donem/{id}", donem).with(admin(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("1 branş")));

        // Donem kaldirilinca serbest
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/branches/{id}", brans).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"Piyano\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.donem").doesNotExist());
        mockMvc.perform(delete("/api/silme/donem/{id}", donem).with(admin(t))).andExpect(status().isOk());
    }
}
