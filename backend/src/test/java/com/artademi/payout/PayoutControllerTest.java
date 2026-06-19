package com.artademi.payout;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
 * Hakedis (payout) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. Tenant izolasyonu (PK-find + capraz-tenant ogretmen sizinti yok), HESAPLAMA
 * DOGRULUGU (SAATLIK ve CIRO_ORANI, BigDecimal scale 2 hatasiz), grupsuz tahsilat haric tutma,
 * mukerrer 409, ode akisi, onizle (kayitsiz), validasyon ve YETKI (MAAS HASSAS: yalnizca ADMIN)
 * dogrular.
 *
 * <p>Veri ADMIN token ile API uzerinden olusturulur; tenant her istekte tenant_id claim'inden gelir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PayoutControllerTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * Verilen tenant_id + roller ile kimligi dogrulanmis JWT. jwt() post-processor gercek
     * converter'i calistirmadigi icin authority'ler (ROLE_*) ELLE verilir.
     */
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

    // --- Yardimci: referans verisi olusturma ---

    private long createBranch(String tenantId, String ad) throws Exception {
        String body = mockMvc.perform(post("/api/branches")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + ad + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createRoom(String tenantId, String ad) throws Exception {
        String body = mockMvc.perform(post("/api/rooms")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + ad + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** SAATLIK ogretmen olusturur (saatlikUcret verilir). */
    private long createSaatlikTeacher(String tenantId, String ad, String saatlikUcret)
            throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\",\"hakedisTipi\":\"SAATLIK\","
                + "\"saatlikUcret\":" + saatlikUcret + ",\"bransIds\":[]}";
        return postTeacher(tenantId, json);
    }

    /** CIRO_ORANI ogretmen olusturur (ciroOrani verilir). */
    private long createCiroTeacher(String tenantId, String ad, String ciroOrani) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\",\"hakedisTipi\":\"CIRO_ORANI\","
                + "\"ciroOrani\":" + ciroOrani + ",\"bransIds\":[]}";
        return postTeacher(tenantId, json);
    }

    private long postTeacher(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/teachers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Belirli bir ogretmene ait GRUP tipi grup olusturup id'sini dondurur (brans+salon seed eder). */
    private long createGroup(String tenantId, String suffix, long ogretmenId) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        String json = "{\"ad\":\"Grup-" + suffix + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmenId + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}";
        String body = mockMvc.perform(post("/api/groups")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Yetiskin ogrenci olusturur (veli kurali bypass). */
    private long createStudent(String tenantId, String ad, String tc) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"1990-01-01\",\"yetiskinMi\":true}";
        String body = mockMvc.perform(post("/api/students")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void createSession(String tenantId, long grupId, String tarih) throws Exception {
        String json = "{\"grupId\":" + grupId + ",\"tarih\":\"" + tarih + "\"}";
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    /** Bir gruba bagli tahsilat olusturur (CIRO hesabina girer). */
    private void createPayment(String tenantId, long ogrenciId, Long grupId, String tutar,
            String odemeTarihi) throws Exception {
        String grupJson = grupId == null ? "" : ",\"grupId\":" + grupId;
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"tutar\":" + tutar
                + ",\"odemeYontemi\":\"NAKIT\",\"odemeTarihi\":\"" + odemeTarihi + "\"" + grupJson + "}";
        mockMvc.perform(post("/api/payments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    // --- 1. Tenant izolasyonu + 404 PK-find ---

    @Test
    void tenantIzolasyonu_payout404() throws Exception {
        long ogretmen = createSaatlikTeacher(TENANT_A, "Izol", "350.00");
        createGroup(TENANT_A, "izol", ogretmen);
        String body = mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-05\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long payoutA = objectMapper.readTree(body).path("data").path("id").asLong();

        // B baglaminda liste A'nin verisini gormez.
        mockMvc.perform(get("/api/payouts").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/payouts/{id}", payoutA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 2. Capraz-tenant ogretmen -> 404 ---

    @Test
    void caprazTenant_ogretmen404() throws Exception {
        long ogretmenB = createSaatlikTeacher(TENANT_B, "OgrB", "350.00");
        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmenB + ",\"donem\":\"2026-05\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 3. SAATLIK hesaplama (KRITIK) ---

    @Test
    void saatlikHesaplama_4oturum350birim_1400() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000031";
        long ogretmen = createSaatlikTeacher(tenant, "Saatli", "350.00");
        long grup = createGroup(tenant, "saatli", ogretmen);
        createSession(tenant, grup, "2026-04-02");
        createSession(tenant, grup, "2026-04-09");
        createSession(tenant, grup, "2026-04-16");
        createSession(tenant, grup, "2026-04-23");
        // Hedef ay disinda bir oturum (hesaba GIRMEMELI).
        createSession(tenant, grup, "2026-05-01");

        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hakedisTipi").value("SAATLIK"))
                .andExpect(jsonPath("$.data.hesaplananTutar").value(1400.00))
                .andExpect(jsonPath("$.data.dokum.dersSayisi").value(4))
                .andExpect(jsonPath("$.data.dokum.birimUcret").value(350.00))
                .andExpect(jsonPath("$.data.durum").value("HESAPLANDI"));
    }

    // --- 4. CIRO_ORANI hesaplama (KRITIK) + grupsuz tahsilat haric ---

    @Test
    void ciroHesaplama_tahsilat1180kdv18oran40_net1000_hakedis400() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000041";
        long ogretmen = createCiroTeacher(tenant, "Cirolu", "40.00");
        long grup = createGroup(tenant, "cirolu", ogretmen);
        long ogrenci = createStudent(tenant, "Ogr", "41000000001");
        // Gruba bagli tahsilatlar: 1000 + 180 = 1180 (Nisan).
        createPayment(tenant, ogrenci, grup, "1000.00", "2026-04-05");
        createPayment(tenant, ogrenci, grup, "180.00", "2026-04-20");
        // GRUPSUZ tahsilat ayni ayda -> ciro hesabina GIRMEMELI.
        createPayment(tenant, ogrenci, null, "9999.00", "2026-04-10");
        // Hedef ay disinda gruba bagli tahsilat -> GIRMEMELI.
        createPayment(tenant, ogrenci, grup, "5000.00", "2026-05-01");

        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\","
                                + "\"kdvOrani\":18}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hakedisTipi").value("CIRO_ORANI"))
                .andExpect(jsonPath("$.data.dokum.toplamTahsilat").value(1180.00))
                .andExpect(jsonPath("$.data.dokum.kdvOrani").value(18.00))
                .andExpect(jsonPath("$.data.dokum.netCiro").value(1000.00))
                .andExpect(jsonPath("$.data.dokum.oran").value(40.00))
                .andExpect(jsonPath("$.data.hesaplananTutar").value(400.00));
    }

    // --- 5. Mukerrer hesapla -> 409 ---

    @Test
    void mukerrerHesapla_409() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000051";
        long ogretmen = createSaatlikTeacher(tenant, "Muk", "100.00");
        createGroup(tenant, "muk", ogretmen);
        String json = "{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\"}";
        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // --- 6. ode -> ODENDI + odemeTarihi ---

    @Test
    void ode_durumOdendiOdemeTarihiDolu() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000061";
        long ogretmen = createSaatlikTeacher(tenant, "Ode", "100.00");
        createGroup(tenant, "ode", ogretmen);
        String body = mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long payout = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/payouts/{id}/ode", payout).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("ODENDI"))
                .andExpect(jsonPath("$.data.odemeTarihi", notNullValue()));
    }

    // --- 7. onizle KAYDETMEZ ---

    @Test
    void onizle_kaydetmez() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000071";
        long ogretmen = createSaatlikTeacher(tenant, "Oniz", "350.00");
        long grup = createGroup(tenant, "oniz", ogretmen);
        createSession(tenant, grup, "2026-04-02");
        createSession(tenant, grup, "2026-04-09");

        // onizle -> dokum doner ama satir olusturmaz.
        mockMvc.perform(get("/api/payouts/onizle")
                        .param("ogretmenId", String.valueOf(ogretmen))
                        .param("donem", "2026-04")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hesaplananTutar").value(700.00))
                .andExpect(jsonPath("$.data.dokum.dersSayisi").value(2))
                .andExpect(jsonPath("$.data.id").doesNotExist());

        // Liste hala bos.
        mockMvc.perform(get("/api/payouts").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // hesapla -> liste 1.
        mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/payouts").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // --- 8. YETKI (MAAS HASSAS: yalnizca ADMIN) ---

    @Test
    void yetki_yalnizcaAdmin() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000081";
        long ogretmen = createSaatlikTeacher(tenant, "Yetki", "100.00");
        createGroup(tenant, "yetki", ogretmen);
        String hesaplaJson = "{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026-04\"}";

        // FRONTDESK_ACCOUNTING -> 403 (maas gormez).
        mockMvc.perform(post("/api/payouts/hesapla").with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content(hesaplaJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payouts").with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payouts/onizle")
                        .param("ogretmenId", String.valueOf(ogretmen)).param("donem", "2026-04")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());

        // FRONTDESK -> 403.
        mockMvc.perform(post("/api/payouts/hesapla").with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content(hesaplaJson))
                .andExpect(status().isForbidden());

        // TEACHER -> 403.
        mockMvc.perform(get("/api/payouts").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());

        // ADMIN -> 201 ve sonraki uclar calisir.
        String body = mockMvc.perform(post("/api/payouts/hesapla").with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(hesaplaJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long payout = objectMapper.readTree(body).path("data").path("id").asLong();
        mockMvc.perform(get("/api/payouts/{id}", payout).with(admin(tenant)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/payouts").with(admin(tenant)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/payouts/{id}/ode", payout).with(admin(tenant)))
                .andExpect(status().isOk());

        // ADMIN onizle (mukerrer kontrolu yok) -> 200.
        mockMvc.perform(get("/api/payouts/onizle")
                        .param("ogretmenId", String.valueOf(ogretmen)).param("donem", "2026-04")
                        .with(admin(tenant)))
                .andExpect(status().isOk());
    }

    // --- 9. Validasyon ---

    @Test
    void validasyon_eksikAlanVeKotuDonem400() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000091";
        long ogretmen = createSaatlikTeacher(tenant, "Val", "100.00");

        // ogretmenId eksik.
        mockMvc.perform(post("/api/payouts/hesapla").with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"donem\":\"2026-04\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // donem eksik.
        mockMvc.perform(post("/api/payouts/hesapla").with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // gecersiz donem formati.
        mockMvc.perform(post("/api/payouts/hesapla").with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogretmenId\":" + ogretmen + ",\"donem\":\"2026/04\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
