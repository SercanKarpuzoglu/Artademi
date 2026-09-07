package com.artademi.paket;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Ders paketi (kontor).
 *
 * <p>Kilitlenen kurallar: kontor GELDI/GELMEDI'de duser, IZINLI'de dusmez ve varsa GERI ALINIR;
 * ayni dersten iki kontor dusmez; kontor bitince yoklama ENGELLENMEZ; satista pesin tahakkuk
 * uretilir. Kalan ders SAKLANMAZ, kullanim satirlarindan hesaplanir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaketEndpointTest {

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

    private long ogrenciKur(String t, String tc) throws Exception {
        return postId(t, "/api/students", "{\"ad\":\"Ada\",\"soyad\":\"Test\",\"tcKimlikNo\":\""
                + tc + "\",\"dogumTarihi\":\"2010-01-01\",\"yetiskinMi\":true}");
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

    private long oturumAc(String t, long grupId, LocalDate gun) throws Exception {
        return postId(t, "/api/attendance-sessions",
                "{\"grupId\":" + grupId + ",\"tarih\":\"" + gun + "\"}");
    }

    /** Yoklama durumunu gunceller (kontor dusumunu tetikleyen yer). */
    private void durumYaz(String t, long oturumId, long ogrenciId, String durum) throws Exception {
        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", oturumId).with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrenciId + ",\"durum\":\"" + durum + "\"}]"))
                .andExpect(status().isOk());
    }

    private long kalan(String t, long paketId) throws Exception {
        String body = mockMvc.perform(get("/api/paketler/{id}", paketId).with(admin(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("kalanDers").asLong();
    }

    // ---------- satis ----------

    @Test
    void satis_pesinTahakkukURETIR() throws Exception {
        String t = yeniKurum();
        long ogrenci = ogrenciKur(t, "81000000001");

        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Derslik Bale\",\"toplamDers\":10,\"tutar\":4000.00}");

        mockMvc.perform(get("/api/paketler/{id}", paket).with(admin(t)))
                .andExpect(jsonPath("$.data.toplamDers").value(10))
                .andExpect(jsonPath("$.data.kalanDers").value(10))
                .andExpect(jsonPath("$.data.kullanilanDers").value(0))
                // Pesin tahakkuk uretilmis olmali.
                .andExpect(jsonPath("$.data.accrualId").isNumber());
    }

    // ---------- kontor dusumu ----------

    @Test
    void GELDI_kontorDUSER() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "geldi");
        long ogrenci = ogrenciKur(t, "81000000002");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 1));

        durumYaz(t, oturum, ogrenci, "GELDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(9);
    }

    @Test
    void GELMEDI_kontorDUSER_habersizGelmemeYakar() throws Exception {
        // Okul dersi tahsis etti; habersiz gelmeme kontoru yakar.
        String t = yeniKurum();
        long grup = grupKur(t, "gelmedi");
        long ogrenci = ogrenciKur(t, "81000000003");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 2));

        durumYaz(t, oturum, ogrenci, "GELMEDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(9);
    }

    @Test
    void IZINLI_kontorDUSMEZ_onceDusulduyseGERI_ALINIR() throws Exception {
        // ⚠️ Bu testin kilitledigi sey: yoklama duzeltmesi kontoru geri getirmeli.
        // Sayac tutulsaydi geri alma adimi kacar, sapma sessiz kalirdi.
        String t = yeniKurum();
        long grup = grupKur(t, "izinli");
        long ogrenci = ogrenciKur(t, "81000000004");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 3));

        durumYaz(t, oturum, ogrenci, "GELDI");
        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(9);

        durumYaz(t, oturum, ogrenci, "IZINLI");
        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(10);
    }

    @Test
    void ayniDers_IKI_kez_kaydedilse_kontorBIR_duser() throws Exception {
        // Idempotans: yoklama iki kez kaydedilirse kontor iki kez dusmemeli.
        String t = yeniKurum();
        long grup = grupKur(t, "idempotan");
        long ogrenci = ogrenciKur(t, "81000000005");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 4));

        durumYaz(t, oturum, ogrenci, "GELDI");
        durumYaz(t, oturum, ogrenci, "GELDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(9);
    }

    @Test
    void kontorBITINCE_yoklama_ENGELLENMEZ() throws Exception {
        // ⚠️ Yoklama alinamamasi ogretmeni sistem disina iter; paket takibi bunu hak etmez.
        String t = yeniKurum();
        long grup = grupKur(t, "bitti");
        long ogrenci = ogrenciKur(t, "81000000006");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"1 Ders\",\"toplamDers\":1,\"tutar\":400.00}");

        long ilk = oturumAc(t, grup, LocalDate.of(2026, 6, 5));
        durumYaz(t, ilk, ogrenci, "GELDI");
        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isZero();

        // Kontor bitti — yoklama yine de kaydedilebilmeli (200).
        long ikinci = oturumAc(t, grup, LocalDate.of(2026, 6, 6));
        durumYaz(t, ikinci, ogrenci, "GELDI");

        // Kalan negatife DUSMEZ.
        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isZero();
    }

    @Test
    void paketsizOgrenci_yoklamaNORMAL_calisir() throws Exception {
        // Aylik aidatli ogrencide paket mantigi hic devreye girmemeli.
        String t = yeniKurum();
        long grup = grupKur(t, "paketsiz");
        long ogrenci = ogrenciKur(t, "81000000007");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 7));

        durumYaz(t, oturum, ogrenci, "GELDI");
    }

    @Test
    void iptalEdilmisPaketten_kontorDUSMEZ() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "iptal");
        long ogrenci = ogrenciKur(t, "81000000008");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");

        mockMvc.perform(post("/api/paketler/{id}/iptal", paket).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("IPTAL"));

        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 8));
        durumYaz(t, oturum, ogrenci, "GELDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(10);
    }

    @Test
    void suresiDolmusPaketten_kontorDUSMEZ() throws Exception {
        String t = yeniKurum();
        long grup = grupKur(t, "sure");
        long ogrenci = ogrenciKur(t, "81000000009");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        long paket = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00,"
                + "\"sonKullanmaTarihi\":\"2020-01-01\"}");

        long oturum = oturumAc(t, grup, LocalDate.now());
        durumYaz(t, oturum, ogrenci, "GELDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, paket)).isEqualTo(10);
    }

    @Test
    void ikiPaket_FIFO_enEskiONCE_tuketilir() throws Exception {
        // Aksi halde suresi yaklasan paket bosta kalirken yeni paket harcanir.
        String t = yeniKurum();
        long grup = grupKur(t, "fifo");
        long ogrenci = ogrenciKur(t, "81000000010");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");

        long eski = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"Eski\",\"toplamDers\":5,\"tutar\":100.00,"
                + "\"satisTarihi\":\"2026-01-01\"}");
        long yeni = postId(t, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"Yeni\",\"toplamDers\":5,\"tutar\":100.00,"
                + "\"satisTarihi\":\"2026-06-01\"}");

        long oturum = oturumAc(t, grup, LocalDate.of(2026, 6, 9));
        durumYaz(t, oturum, ogrenci, "GELDI");

        org.assertj.core.api.Assertions.assertThat(kalan(t, eski)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(kalan(t, yeni)).isEqualTo(5);
    }

    // ---------- izolasyon ve yetki ----------

    @Test
    void baskaKurumunPaketi_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long ogrenci = ogrenciKur(a, "81000000011");
        long paket = postId(a, "/api/paketler", "{\"ogrenciId\":" + ogrenci
                + ",\"ad\":\"10 Ders\",\"toplamDers\":10,\"tutar\":4000.00}");

        mockMvc.perform(get("/api/paketler/{id}", paket).with(admin(b)))
                .andExpect(status().isNotFound());
    }

    @Test
    void baskaKurumunOgrencisinePaket_404() throws Exception {
        String a = yeniKurum();
        String b = yeniKurum();
        long aOgrenci = ogrenciKur(a, "81000000012");

        mockMvc.perform(post("/api/paketler").with(admin(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + aOgrenci
                                + ",\"ad\":\"X\",\"toplamDers\":5,\"tutar\":100.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onBuro_ERISEMEZ_403() throws Exception {
        // Paket satisi tahakkuk uretir; PARASAL islemdir.
        String t = yeniKurum();
        mockMvc.perform(get("/api/paketler").with(token(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }
}
