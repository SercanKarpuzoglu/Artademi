package com.artademi.telafi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * Telafi ders hakki.
 *
 * <p>Kilitlenen kurallar: hak OTOMATIK dogmaz (aday listesi yalnizca oneridir), ayni
 * devamsizliktan IKI hak verilemez, kullanilmis/iptal/suresi dolmus hak kullanilamaz.
 * Bunlarin hepsi "bir devamsizlik iki telafi dersi dogurur" hatasini onler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TelafiEndpointTest {

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

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> yetkiler = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(yetkiler);
    }

    private static RequestPostProcessor admin(String t) {
        return token(t, "ADMIN");
    }

    private static String yeniKurum() {
        return UUID.randomUUID().toString();
    }

    private long postId(String t, String yol, String json) throws Exception {
        String body = mockMvc.perform(post(yol).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long ogrenciKur(String t, String ad, String tc) throws Exception {
        return postId(t, "/api/students", "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\","
                + "\"tcKimlikNo\":\"" + tc + "\",\"dogumTarihi\":\"2010-01-01\","
                + "\"yetiskinMi\":true}");
    }

    private long grupKur(String t, String ek) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans-" + ek + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca-" + ek + "\",\"soyad\":\"H\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon-" + ek + "\"}");
        return postId(t, "/api/groups", "{\"ad\":\"Grup-" + ek + "\",\"tip\":\"GRUP\",\"bransId\":"
                + brans + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon
                + ",\"aylikAidat\":500.00}");
    }

    /** Oturum acilinca kayitli ogrenciler GELMEDI varsayilaniyla uretilir. */
    private long oturumAc(String t, long grupId, LocalDate gun) throws Exception {
        return postId(t, "/api/attendance-sessions",
                "{\"grupId\":" + grupId + ",\"tarih\":\"" + gun + "\"}");
    }

    // ---------- hak verme ----------

    @Test
    void hakVer_veListele() throws Exception {
        String t = yeniKurum();
        long ogrenci = ogrenciKur(t, "Ada", "71000000001");

        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + ",\"aciklama\":\"Kurum kaynaklı iptal\"}");

        mockMvc.perform(get("/api/telafi").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].durum").value("BEKLIYOR"))
                .andExpect(jsonPath("$.data[0].suresiDoldu").value(false))
                .andExpect(jsonPath("$.data[0].ogrenciAdSoyad").value("Ada Test"));
    }

    @Test
    void devamsizliktanHak_ayniKaynaktanIKINCI_kez_409() throws Exception {
        // ⚠️ Aksi halde bir devamsizlik IKI telafi dersi dogururdu.
        String t = yeniKurum();
        long grup = grupKur(t, "cift");
        long ogrenci = ogrenciKur(t, "Ada", "71000000002");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 5, 4));

        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + ",\"kaynakOturumId\":" + oturum + "}");

        mockMvc.perform(post("/api/telafi").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"kaynakOturumId\":" + oturum + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void kaynaksizHak_BIRDEN_FAZLA_verilebilir() throws Exception {
        // Devamsizliga bagli olmayan haklar (tatil, kurum iptali) kisitlanmaz.
        String t = yeniKurum();
        long ogrenci = ogrenciKur(t, "Ada", "71000000003");

        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");
        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");

        mockMvc.perform(get("/api/telafi").with(admin(t)))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ---------- kullanim ----------

    @Test
    void hakKullanildi_dersKANIT_olarakSaklanir() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "kullan");
        long ogrenci = ogrenciKur(t, "Ada", "71000000004");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long telafiDersi = oturumAc(t, grup, LocalDate.of(2026, 5, 11));
        long hak = postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");

        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullanilanOturumId\":" + telafiDersi + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("KULLANILDI"))
                .andExpect(jsonPath("$.data.kullanilanOturumId").value(telafiDersi))
                .andExpect(jsonPath("$.data.kullanimTarihi").isNotEmpty());
    }

    @Test
    void kullanilmisHak_IKINCI_kez_kullanilamaz_409() throws Exception {
        // Ayni telafiyi iki kez saymak olurdu.
        String t = yeniKurum();
        long grup = grupKur(t, "iki-kullan");
        long ogrenci = ogrenciKur(t, "Ada", "71000000005");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long ders = oturumAc(t, grup, LocalDate.of(2026, 5, 12));
        long hak = postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");
        String govde = "{\"kullanilanOturumId\":" + ders + "}";

        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isConflict());
    }

    @Test
    void suresiDolmusHak_KULLANILAMAZ_409() throws Exception {
        // Sure koymanin anlami budur.
        String t = yeniKurum();
        long grup = grupKur(t, "sure");
        long ogrenci = ogrenciKur(t, "Ada", "71000000006");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long ders = oturumAc(t, grup, LocalDate.of(2026, 5, 13));
        long hak = postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci
                + ",\"sonKullanmaTarihi\":\"2020-01-01\"}");

        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullanilanOturumId\":" + ders + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void suresiDolmus_listede_isaretlenir() throws Exception {
        // ⚠️ Durum olarak SAKLANMAZ; okuma aninda hesaplanir (expire eden job yok).
        String t = yeniKurum();
        long ogrenci = ogrenciKur(t, "Ada", "71000000007");
        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci
                + ",\"sonKullanmaTarihi\":\"2020-01-01\"}");

        mockMvc.perform(get("/api/telafi").with(admin(t)))
                .andExpect(jsonPath("$.data[0].durum").value("BEKLIYOR"))
                .andExpect(jsonPath("$.data[0].suresiDoldu").value(true));
    }

    @Test
    void iptalEdilmisHak_kullanilamaz_409() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "iptal");
        long ogrenci = ogrenciKur(t, "Ada", "71000000008");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long ders = oturumAc(t, grup, LocalDate.of(2026, 5, 14));
        long hak = postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");

        mockMvc.perform(post("/api/telafi/{id}/iptal", hak).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("IPTAL"));

        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullanilanOturumId\":" + ders + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void kullanilmisHak_IPTAL_edilemez_409() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "kullanildi-iptal");
        long ogrenci = ogrenciKur(t, "Ada", "71000000009");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long ders = oturumAc(t, grup, LocalDate.of(2026, 5, 15));
        long hak = postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");

        mockMvc.perform(post("/api/telafi/{id}/kullan", hak).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullanilanOturumId\":" + ders + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/telafi/{id}/iptal", hak).with(admin(t)))
                .andExpect(status().isConflict());
    }

    // ---------- aday listesi ----------

    @Test
    void adaylar_devamsizlikGosterir_hakVerilinceLISTEDEN_CIKAR() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "aday");
        long ogrenci = ogrenciKur(t, "Ada", "71000000010");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long oturum = oturumAc(t, grup, LocalDate.now().minusDays(3));

        mockMvc.perform(get("/api/telafi/adaylar").with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ogrenciId").value(ogrenci));

        postId(t, "/api/telafi", "{\"ogrenciId\":" + ogrenci + ",\"kaynakOturumId\":" + oturum + "}");

        // Hak verildi -> aday listesinde artik GORUNMEZ.
        mockMvc.perform(get("/api/telafi/adaylar").with(admin(t)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ---------- izolasyon ve yetki ----------

    @Test
    void baskaKurumunHakki_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long ogrenci = ogrenciKur(a, "Ada", "71000000011");
        long hak = postId(a, "/api/telafi", "{\"ogrenciId\":" + ogrenci + "}");

        mockMvc.perform(get("/api/telafi/{id}", hak).with(admin(b)))
                .andExpect(status().isNotFound());
    }

    @Test
    void baskaKurumunOgrencisineHak_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long aOgrenci = ogrenciKur(a, "Ada", "71000000012");

        mockMvc.perform(post("/api/telafi").with(admin(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + aOgrenci + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onBuro_ERISEBILIR_parasalBilgiYok() throws Exception {
        String t = yeniKurum();
        mockMvc.perform(get("/api/telafi").with(token(t, "FRONTDESK")))
                .andExpect(status().isOk());
    }

    @Test
    void ogretmen_ERISEMEZ_403() throws Exception {
        String t = yeniKurum();
        mockMvc.perform(get("/api/telafi").with(token(t, "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
