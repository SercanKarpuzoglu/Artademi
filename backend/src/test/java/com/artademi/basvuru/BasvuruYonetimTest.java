package com.artademi.basvuru;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Kurum ici basvuru yonetimi: liste, durum, ogrenciye donusturme.
 *
 * <p>Kilitlenen kurallar: capraz-tenant erisim yok; OGRENCIYE_DONUSTU elle atanamaz;
 * ayni basvuru iki kez ogrenciye donusturulemez; cocuk ogrencide veli bilgisi zorunlulugu
 * ogrenci formundakiyle AYNI kalir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BasvuruYonetimTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TenantRepository tenantRepo;

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private Tenant kurumOlustur(String slug) {
        Tenant t = Tenant.create("Kurum " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        t.setBasvuruSlug(slug);
        return tenantRepo.save(t);
    }

    /** Public uctan basvuru gonderip id'sini doner. */
    private long basvuruGonder(Tenant t, String ad, String telefon, String ip) throws Exception {
        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + ad + "\",\"soyad\":\"Yılmaz\",\"telefon\":\""
                                + telefon + "\"}"))
                .andExpect(status().isOk());

        String cevap = mockMvc.perform(get("/api/basvurular")
                        .with(token(t.getId().toString(), "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cevap).path("data").get(0).path("id").asLong();
    }

    @Test
    void liste_kendiKurumununBasvurularini_gorur() throws Exception {
        Tenant t = kurumOlustur("liste-" + UUID.randomUUID());
        basvuruGonder(t, "Zeynep", "05552220001", "198.51.100.1");

        mockMvc.perform(get("/api/basvurular").with(token(t.getId().toString(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ad").value("Zeynep"))
                .andExpect(jsonPath("$.data[0].durum").value("YENI"));
    }

    @Test
    void liste_baskaKurumunBasvurusunu_GORMEZ() throws Exception {
        Tenant a = kurumOlustur("izol-a-" + UUID.randomUUID());
        Tenant b = kurumOlustur("izol-b-" + UUID.randomUUID());
        basvuruGonder(a, "Gizli", "05552220002", "198.51.100.2");

        mockMvc.perform(get("/api/basvurular").with(token(b.getId().toString(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void detay_baskaKurum_404() throws Exception {
        Tenant a = kurumOlustur("detay-a-" + UUID.randomUUID());
        Tenant b = kurumOlustur("detay-b-" + UUID.randomUUID());
        long id = basvuruGonder(a, "Ada", "05552220003", "198.51.100.3");

        mockMvc.perform(get("/api/basvurular/{id}", id).with(token(b.getId().toString(), "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void durum_arandiYapilabilir() throws Exception {
        Tenant t = kurumOlustur("durum-" + UUID.randomUUID());
        long id = basvuruGonder(t, "Ada", "05552220004", "198.51.100.4");

        mockMvc.perform(patch("/api/basvurular/{id}/durum", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durum\":\"ARANDI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("ARANDI"));
    }

    @Test
    void durum_ogrenciyeDonustuELLE_atanamaz_409() throws Exception {
        // Aksi halde ogrencisi olmayan "donusturuldu" kayitlari olusur, liste yalan soyler.
        Tenant t = kurumOlustur("durum-elle-" + UUID.randomUUID());
        long id = basvuruGonder(t, "Ada", "05552220005", "198.51.100.5");

        mockMvc.perform(patch("/api/basvurular/{id}/durum", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durum\":\"OGRENCIYE_DONUSTU\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void donustur_yetiskin_ogrenciOlusur() throws Exception {
        Tenant t = kurumOlustur("donustur-" + UUID.randomUUID());
        long id = basvuruGonder(t, "Ada", "05552220006", "198.51.100.6");

        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tcKimlikNo\":\"12345678901\",\"dogumTarihi\":\"1995-05-05\","
                                + "\"yetiskinMi\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("OGRENCIYE_DONUSTU"))
                .andExpect(jsonPath("$.data.ogrenciId").isNumber());
    }

    @Test
    void donustur_cocukVeliBilgisiYok_400() throws Exception {
        // Ogrenci formundaki AYNI kural gecerli: yetiskin degilse anne VEYA baba ad+TC zorunlu.
        Tenant t = kurumOlustur("donustur-cocuk-" + UUID.randomUUID());
        long id = basvuruGonder(t, "Minik", "05552220007", "198.51.100.7");

        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tcKimlikNo\":\"12345678902\",\"dogumTarihi\":\"2015-05-05\","
                                + "\"yetiskinMi\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void donustur_ikinciKez_409() throws Exception {
        // Mukerrer donusum ayni kisinin IKI ogrenci kaydi demektir; tahakkuk/yoklama boluner.
        Tenant t = kurumOlustur("donustur-iki-" + UUID.randomUUID());
        long id = basvuruGonder(t, "Ada", "05552220008", "198.51.100.8");
        String govde = "{\"tcKimlikNo\":\"12345678903\",\"dogumTarihi\":\"1995-05-05\","
                + "\"yetiskinMi\":true}";

        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", id)
                        .with(token(t.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isConflict());
    }

    @Test
    void donustur_baskaKurum_404() throws Exception {
        Tenant a = kurumOlustur("donustur-a-" + UUID.randomUUID());
        Tenant b = kurumOlustur("donustur-b-" + UUID.randomUUID());
        long id = basvuruGonder(a, "Ada", "05552220009", "198.51.100.9");

        mockMvc.perform(post("/api/basvurular/{id}/ogrenciye-donustur", id)
                        .with(token(b.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tcKimlikNo\":\"12345678904\",\"dogumTarihi\":\"1995-05-05\","
                                + "\"yetiskinMi\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ogretmen_basvurulariGOREMEZ_403() throws Exception {
        Tenant t = kurumOlustur("rol-" + UUID.randomUUID());

        mockMvc.perform(get("/api/basvurular").with(token(t.getId().toString(), "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void yeniSayisi_rozetIcin() throws Exception {
        Tenant t = kurumOlustur("sayi-" + UUID.randomUUID());
        basvuruGonder(t, "Ada", "05552220010", "198.51.100.10");

        mockMvc.perform(get("/api/basvurular/yeni-sayisi")
                        .with(token(t.getId().toString(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }
}
