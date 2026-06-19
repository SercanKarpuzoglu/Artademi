package com.artademi.finance;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
 * Otomatik aylik tahakkuk uretimi entegrasyon testleri — gercek PostgreSQL (Testcontainers) +
 * MockMvc + JWT post-processor. Tenant izolasyonu, mutlu yol, IDEMPOTENT mukerrer atlama (kritik),
 * sadece AKTIF ogrenci/AKTIF kayit/GRUP-aidatli kayitlarin uretilmesi, onizlemenin kayit
 * olusturmamasi ve YETKI (yalnizca ADMIN) dogrular.
 *
 * <p>Her test KENDI tenant'ini kullanir: uret tenant'taki TUM uygun kayitlari tarar, bu yuzden
 * testler arasi sizinti olmamasi icin tenant'lar ayriktir. Veri ADMIN token ile API uzerinden olusur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccrualGenerationTest {

    private static final String DONEM = "2026-07";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private static RequestPostProcessor admin(String tenantId) {
        return token(tenantId, "ADMIN");
    }

    // --- Yardimci: referans verisi ---

    private long createBranch(String t, String ad) throws Exception {
        return idOf(mockMvc.perform(post("/api/branches").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"" + ad + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long createRoom(String t, String ad) throws Exception {
        return idOf(mockMvc.perform(post("/api/rooms").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"" + ad + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long createTeacher(String t, String ad) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\",\"hakedisTipi\":\"SAATLIK\","
                + "\"saatlikUcret\":200.00,\"bransIds\":[]}";
        return idOf(mockMvc.perform(post("/api/teachers").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** GRUP tipi grup (aylikAidat verilen tutar). */
    private long createGrupGroup(String t, String suffix, String aylikAidat) throws Exception {
        long brans = createBranch(t, "Brans-" + suffix);
        long ogretmen = createTeacher(t, "Ogr-" + suffix);
        long salon = createRoom(t, "Salon-" + suffix);
        String json = "{\"ad\":\"Grup-" + suffix + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon
                + ",\"aylikAidat\":" + aylikAidat + "}";
        return idOf(mockMvc.perform(post("/api/groups").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** OZEL tipi grup (aylikAidat YOK, dersBasiUcret var). */
    private long createOzelGroup(String t, String suffix) throws Exception {
        long brans = createBranch(t, "BransO-" + suffix);
        long ogretmen = createTeacher(t, "OgrO-" + suffix);
        String json = "{\"ad\":\"Ozel-" + suffix + "\",\"tip\":\"OZEL\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"dersBasiUcret\":300.00}";
        return idOf(mockMvc.perform(post("/api/groups").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Yetiskin ogrenci (varsayilan statu DENEME). */
    private long createStudent(String t, String ad, String tc) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"1990-01-01\",\"yetiskinMi\":true}";
        return idOf(mockMvc.perform(post("/api/students").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void setStatus(String t, long ogrenciId, String statu) throws Exception {
        mockMvc.perform(patch("/api/students/{id}/status", ogrenciId).with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + statu + "\"}"))
                .andExpect(status().isOk());
    }

    private long enroll(String t, long ogrenciId, long grupId) throws Exception {
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"grupId\":" + grupId + "}";
        return idOf(mockMvc.perform(post("/api/enrollments").with(admin(t))
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void leave(String t, long enrollmentId) throws Exception {
        mockMvc.perform(patch("/api/enrollments/{id}/leave", enrollmentId).with(admin(t)))
                .andExpect(status().isOk());
    }

    /** Bir ogrencinin AKTIF + GRUP-aidatli kaydini hazirlar; aylikAidat=1500 ile grup 1. */
    private long aktifOgrenciGrupKaydi(String t, String aylikAidat) throws Exception {
        long grup = createGrupGroup(t, "x", aylikAidat);
        long ogrenci = createStudent(t, "Ada", randomTc());
        setStatus(t, ogrenci, "AKTIF");
        enroll(t, ogrenci, grup);
        return ogrenci;
    }

    private int accrualCount(String t, long ogrenciId) throws Exception {
        String body = mockMvc.perform(get("/api/accruals").param("ogrenciId", String.valueOf(ogrenciId))
                        .with(admin(t)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").size();
    }

    private long idOf(String body) throws Exception {
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String randomTc() {
        // 11 haneli benzersiz TC (sadece \d{11} format dogrulamasi var, checksum yok).
        return String.format("%011d", Math.abs(System.nanoTime()) % 100000000000L);
    }

    // --- Testler ---

    @Test
    void tenantIzolasyonu_AdaUretimiBdeGorunmez() throws Exception {
        String a = "11111111-0000-0000-0000-0000000000a1";
        String b = "11111111-0000-0000-0000-0000000000b1";
        long ogrenciA = aktifOgrenciGrupKaydi(a, "1500.00");
        long ogrenciB = aktifOgrenciGrupKaydi(b, "1500.00");

        mockMvc.perform(post("/api/accruals/uret").with(admin(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));

        // B'de hicbir tahakkuk olusmadi (A'nin uretimi B'ye dokunmaz).
        org.junit.jupiter.api.Assertions.assertEquals(0, accrualCount(b, ogrenciB));
        org.junit.jupiter.api.Assertions.assertEquals(1, accrualCount(a, ogrenciA));
    }

    @Test
    void mutluYol_aktifGrupKaydiUretilir() throws Exception {
        String t = "22222222-0000-0000-0000-000000000001";
        long ogrenci = aktifOgrenciGrupKaydi(t, "1500.00");

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.donem").value(DONEM))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1))
                .andExpect(jsonPath("$.data.atlananSayisi").value(0))
                .andExpect(jsonPath("$.data.toplamTutar").value(1500.00))
                .andExpect(jsonPath("$.data.ozet", hasSize(1)))
                .andExpect(jsonPath("$.data.ozet[0].tutar").value(1500.00));

        // Bakiyeye yansidi (1 tahakkuk, odeme yok -> 1500.00 borc).
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bakiye").value(1500.00));
    }

    @Test
    void mukerrerAtlama_ikinciUretimSifirUretir() throws Exception {
        String t = "33333333-0000-0000-0000-000000000001";
        long ogrenci = aktifOgrenciGrupKaydi(t, "1500.00");

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, accrualCount(t, ogrenci));

        // Ikinci kez: hicbir sey uretilmez, hepsi atlanir (IDEMPOTENT — kritik).
        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0))
                .andExpect(jsonPath("$.data.atlananSayisi").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, accrualCount(t, ogrenci));
    }

    @Test
    void aktifOlmayanOgrenci_uretilmez() throws Exception {
        String t = "44444444-0000-0000-0000-000000000001";
        long grup = createGrupGroup(t, "x", "1500.00");
        long ogrenci = createStudent(t, "Deneme", randomTc()); // statu DENEME (AKTIF degil)
        enroll(t, ogrenci, grup);

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0));
    }

    @Test
    void ozelGrupKaydi_aidatYok_uretilmez() throws Exception {
        String t = "55555555-0000-0000-0000-000000000001";
        long ozel = createOzelGroup(t, "x");
        long ogrenci = createStudent(t, "Ozel", randomTc());
        setStatus(t, ogrenci, "AKTIF");
        enroll(t, ogrenci, ozel);

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0));
    }

    @Test
    void ayrilanKayit_uretilmez() throws Exception {
        String t = "66666666-0000-0000-0000-000000000001";
        long grup = createGrupGroup(t, "x", "1500.00");
        long ogrenci = createStudent(t, "Ayrilan", randomTc());
        setStatus(t, ogrenci, "AKTIF");
        long kayit = enroll(t, ogrenci, grup);
        leave(t, kayit); // durum AYRILDI

        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(0));
    }

    @Test
    void onizleme_kayitOlusturmaz() throws Exception {
        String t = "77777777-0000-0000-0000-000000000001";
        long ogrenci = aktifOgrenciGrupKaydi(t, "1500.00");

        // Onizle: 1 uretilecek der ama KAYIT OLUSTURMAZ.
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", DONEM).with(admin(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1))
                .andExpect(jsonPath("$.data.toplamTutar").value(1500.00));
        org.junit.jupiter.api.Assertions.assertEquals(0, accrualCount(t, ogrenci));

        // Uret: artik gercekten olusur.
        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"" + DONEM + "\"}"))
                .andExpect(jsonPath("$.data.uretilenSayisi").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(1, accrualCount(t, ogrenci));
    }

    @Test
    void yetki_sadeceAdmin() throws Exception {
        String t = "88888888-0000-0000-0000-000000000001";
        String body = "{\"donem\":\"" + DONEM + "\"}";

        // ADMIN -> 200.
        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", DONEM).with(admin(t)))
                .andExpect(status().isOk());

        // FRONTDESK_ACCOUNTING -> 403 (toplu uretim ADMIN isi).
        mockMvc.perform(post("/api/accruals/uret").with(token(t, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accruals/uret-onizle").param("donem", DONEM)
                        .with(token(t, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());

        // FRONTDESK -> 403.
        mockMvc.perform(post("/api/accruals/uret").with(token(t, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        // TEACHER -> 403.
        mockMvc.perform(post("/api/accruals/uret").with(token(t, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void gecersizDonem_400() throws Exception {
        String t = "99999999-0000-0000-0000-000000000001";
        mockMvc.perform(post("/api/accruals/uret").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"2026/06\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
