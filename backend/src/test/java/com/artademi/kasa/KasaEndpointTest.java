package com.artademi.kasa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Kasa modulu.
 *
 * <p>En kritik iki davranis: <b>bakiye formulu</b> (saklanmaz, hesaplanir — tahsilat girer,
 * gider cikar, transfer iki kasayi birden etkiler) ve <b>transferin atomikligi</b> (tek bacak
 * kalirsa kasalar arasinda kaybolmus para olusur).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KasaEndpointTest {

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

    /** Her test KENDI kurumunu kullanir; testler birbirinin kaydini gormesin. */
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

    private long kasaKur(String t, String ad, String acilis) throws Exception {
        return postId(t, "/api/kasalar", "{\"ad\":\"" + ad + "\",\"tip\":\"NAKIT\","
                + "\"acilisBakiyesi\":" + acilis + "}");
    }

    private long ogrenciKur(String t, String tc) throws Exception {
        return postId(t, "/api/students", "{\"ad\":\"Ada\",\"soyad\":\"Test\",\"tcKimlikNo\":\""
                + tc + "\",\"dogumTarihi\":\"1990-01-01\",\"yetiskinMi\":true}");
    }

    private String bakiye(String t, long kasaId) throws Exception {
        String body = mockMvc.perform(get("/api/kasalar/{id}", kasaId).with(admin(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("bakiye").asText();
    }

    // ---------- bakiye formulu ----------

    @Test
    void acilisBakiyesi_baslangicNoktasidir() throws Exception {
        String t = yeniKurum();
        long kasa = kasaKur(t, "Merkez Nakit", "1000.00");

        // Gecmis hareketleri girmek zorunda kalmadan dogru bakiye gostermenin tek yolu.
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, kasa)))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void tahsilatKasayaGIRER_giderCIKAR() throws Exception {
        String t = yeniKurum();
        long kasa = kasaKur(t, "Kasa", "100.00");
        long ogrenci = ogrenciKur(t, "61000000001");

        postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci + ",\"tutar\":250.00,"
                + "\"odemeYontemi\":\"NAKIT\",\"kasaId\":" + kasa + "}");
        postId(t, "/api/expenses", "{\"tutar\":50.00,\"kategori\":\"Kira\",\"kasaId\":" + kasa + "}");

        // 100 + 250 - 50 = 300
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, kasa)))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void kasasizTahsilat_bakiyeyeETKIETMEZ() throws Exception {
        // Kasa kullanmak zorunlu degil; kasasiz tahsilat hicbir kasanin bakiyesini bozmamali.
        String t = yeniKurum();
        long kasa = kasaKur(t, "Kasa", "100.00");
        long ogrenci = ogrenciKur(t, "61000000002");

        postId(t, "/api/payments", "{\"ogrenciId\":" + ogrenci
                + ",\"tutar\":999.00,\"odemeYontemi\":\"NAKIT\"}");

        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, kasa)))
                .isEqualByComparingTo("100.00");
    }

    // ---------- transfer ----------

    @Test
    void transfer_IKI_kasayiBirdenEtkiler() throws Exception {
        String t = yeniKurum();
        long nakit = kasaKur(t, "Nakit", "500.00");
        long banka = kasaKur(t, "Banka", "0.00");

        mockMvc.perform(post("/api/kasalar/transfer").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kaynakKasaId\":" + nakit + ",\"hedefKasaId\":" + banka
                                + ",\"tutar\":200.00}"))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, nakit)))
                .isEqualByComparingTo("300.00");
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, banka)))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void transferSilme_IKI_bacagiBirdenSiler() throws Exception {
        // ⚠️ Tek bacak silinseydi kasalar arasinda kaybolmus para olusurdu.
        String t = yeniKurum();
        long nakit = kasaKur(t, "Nakit", "500.00");
        long banka = kasaKur(t, "Banka", "0.00");

        String cevap = mockMvc.perform(post("/api/kasalar/transfer").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kaynakKasaId\":" + nakit + ",\"hedefKasaId\":" + banka
                                + ",\"tutar\":200.00}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long birBacak = objectMapper.readTree(cevap).path("data").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/kasalar/hareketler/{id}", birBacak).with(admin(t)))
                .andExpect(status().isOk());

        // Iki kasa da baslangic bakiyesine donmeli.
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, nakit)))
                .isEqualByComparingTo("500.00");
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, banka)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void ayniKasayaTransfer_400() throws Exception {
        String t = yeniKurum();
        long kasa = kasaKur(t, "Kasa", "100.00");

        mockMvc.perform(post("/api/kasalar/transfer").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kaynakKasaId\":" + kasa + ",\"hedefKasaId\":" + kasa
                                + ",\"tutar\":10.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duzeltme_yonuneGoreEtkiEder() throws Exception {
        String t = yeniKurum();
        long kasa = kasaKur(t, "Kasa", "100.00");

        mockMvc.perform(post("/api/kasalar/{id}/duzeltme", kasa).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yon\":\"CIKIS\",\"tutar\":30.00,\"aciklama\":\"Banka masrafı\"}"))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(bakiye(t, kasa)))
                .isEqualByComparingTo("70.00");
    }

    // ---------- izolasyon ve yetki ----------

    @Test
    void baskaKurumunKasasi_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long kasa = kasaKur(a, "A Kasa", "100.00");

        mockMvc.perform(get("/api/kasalar/{id}", kasa).with(admin(b)))
                .andExpect(status().isNotFound());
    }

    @Test
    void baskaKurumunKasasinaTahsilat_404() throws Exception {
        // Istemci baska kurumun kasa id'sini gondererek capraz-tenant bag KURAMAZ.
        String a = yeniKurum();
        String b = yeniKurum();
        long aKasa = kasaKur(a, "A Kasa", "0.00");
        long bOgrenci = ogrenciKur(b, "61000000003");

        mockMvc.perform(post("/api/payments").with(admin(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + bOgrenci + ",\"tutar\":100.00,"
                                + "\"odemeYontemi\":\"NAKIT\",\"kasaId\":" + aKasa + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onBuro_kasalariGOREMEZ_403() throws Exception {
        // Kasa bakiyesi PARASAL bilgidir.
        String t = yeniKurum();
        mockMvc.perform(get("/api/kasalar").with(token(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ayniIsimdeIkinciKasa_409() throws Exception {
        String t = yeniKurum();
        kasaKur(t, "Merkez", "0.00");

        mockMvc.perform(post("/api/kasalar").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Merkez\",\"tip\":\"BANKA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pasiflestirme_kaydiSILMEZ() throws Exception {
        // Gecmis tahsilat/giderler kasaya bagli kalmali.
        String t = yeniKurum();
        long kasa = kasaKur(t, "Eski Kasa", "50.00");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/kasalar/{id}/durum", kasa).param("aktif", "false")
                        .with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        mockMvc.perform(get("/api/kasalar/{id}", kasa).with(admin(t)))
                .andExpect(status().isOk());
    }
}
