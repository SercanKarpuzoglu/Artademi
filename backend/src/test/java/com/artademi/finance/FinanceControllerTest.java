package com.artademi.finance;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Tahsilat/Muhasebe (finance) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc +
 * JWT post-processor. Tenant izolasyonu (PK-find + capraz-tenant ogrenci/grup/accrual sizinti yok),
 * BAKIYE DOGRULUGU (BigDecimal scale 2, yuvarlama hatasiz), tahakkuga bagli tahsilat kurali (-> 400),
 * tutar pozitiflik (-> 400), filtreler ve YETKI (PARA HASSAS: yalnizca ADMIN/FRONTDESK_ACCOUNTING)
 * dogrular.
 *
 * <p>Veri ADMIN token ile API uzerinden olusturulur; tenant her istekte tenant_id claim'inden gelir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinanceControllerTest {

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

    private long createTeacher(String tenantId, String ad) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\",\"hakedisTipi\":\"SAATLIK\","
                + "\"saatlikUcret\":200.00,\"bransIds\":[]}";
        String body = mockMvc.perform(post("/api/teachers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Bir tenant'ta GRUP tipi grup olusturup id'sini dondurur (brans+ogretmen+salon seed eder). */
    private long createGroup(String tenantId, String suffix) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long ogretmen = createTeacher(tenantId, "Ogretmen-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        String json = "{\"ad\":\"Grup-" + suffix + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":500.00}";
        String body = mockMvc.perform(post("/api/groups")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Yetiskin (veli kurali bypass) ogrenci olusturur; varsayilan statu DENEME. */
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

    private long createAccrual(String tenantId, long ogrenciId, String tutar, String donem)
            throws Exception {
        String donemJson = donem == null ? "" : ",\"donem\":\"" + donem + "\"";
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"tutar\":" + tutar + donemJson + "}";
        String body = mockMvc.perform(post("/api/accruals")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createPayment(String tenantId, long ogrenciId, String tutar, String yontem,
            String odemeTarihi) throws Exception {
        String tarihJson = odemeTarihi == null ? "" : ",\"odemeTarihi\":\"" + odemeTarihi + "\"";
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"tutar\":" + tutar
                + ",\"odemeYontemi\":\"" + yontem + "\"" + tarihJson + "}";
        String body = mockMvc.perform(post("/api/payments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createExpense(String tenantId, String tutar, String kategori, String giderTarihi)
            throws Exception {
        StringBuilder json = new StringBuilder("{\"tutar\":" + tutar);
        if (kategori != null) {
            json.append(",\"kategori\":\"").append(kategori).append("\"");
        }
        if (giderTarihi != null) {
            json.append(",\"giderTarihi\":\"").append(giderTarihi).append("\"");
        }
        json.append("}");
        String body = mockMvc.perform(post("/api/expenses")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // --- 1. Tenant izolasyonu + 404 PK-find ---

    @Test
    void tenantIzolasyonu_accrualPaymentExpense404() throws Exception {
        long ogrenci = createStudent(TENANT_A, "Izol", "11000000001");
        long accrualA = createAccrual(TENANT_A, ogrenci, "100.00", "2026-06");
        long paymentA = createPayment(TENANT_A, ogrenci, "50.00", "NAKIT", "2026-06-01");
        long expenseA = createExpense(TENANT_A, "75.00", "Kira", "2026-06-01");

        // B baglaminda listeler A'nin verisini gormez.
        mockMvc.perform(get("/api/accruals").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/payments").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/expenses").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'leriyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/accruals/{id}", accrualA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/payments/{id}", paymentA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/expenses/{id}", expenseA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 2. Capraz-tenant referans -> 404 ---

    @Test
    void caprazTenant_ogrenciGrupAccrual404() throws Exception {
        long ogrenciB = createStudent(TENANT_B, "OgrB", "12000000001");
        long grupB = createGroup(TENANT_B, "capraz");
        long ogrenciA = createStudent(TENANT_A, "OgrA", "12000000002");

        // A'da B'nin ogrencisiyle tahakkuk -> 404.
        mockMvc.perform(post("/api/accruals")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenciB + ",\"tutar\":100.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // A'da B'nin grubuyla tahakkuk -> 404.
        mockMvc.perform(post("/api/accruals")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenciA + ",\"grupId\":" + grupB
                                + ",\"tutar\":100.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // A'da B'nin tahakkuguyla tahsilat -> 404.
        long accrualB = createAccrual(TENANT_B, ogrenciB, "100.00", null);
        mockMvc.perform(post("/api/payments")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenciA + ",\"accrualId\":" + accrualB
                                + ",\"tutar\":50.00,\"odemeYontemi\":\"NAKIT\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 3. BAKIYE DOGRULUGU (KRITIK) ---

    @Test
    void bakiyeDogrulugu_scale2HatasizYuvarlama() throws Exception {
        String tenant = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        long ogrenci = createStudent(tenant, "Bakiye", "13000000001");
        createAccrual(tenant, ogrenci, "100.00", "2026-06");
        createAccrual(tenant, ogrenci, "50.50", "2026-07");
        createPayment(tenant, ogrenci, "30.00", "NAKIT", "2026-06-01");

        // toplamTahakkuk 150.50, toplamOdeme 30.00, bakiye 120.50 (tam scale 2).
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ogrenciId").value(ogrenci))
                .andExpect(jsonPath("$.data.toplamTahakkuk").value(150.50))
                .andExpect(jsonPath("$.data.toplamOdeme").value(30.00))
                .andExpect(jsonPath("$.data.bakiye").value(120.50));

        // finance ozeti: listeler + ayni bakiye.
        mockMvc.perform(get("/api/students/{id}/finance", ogrenci).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ogrenciId").value(ogrenci))
                .andExpect(jsonPath("$.data.tahakkuklar", hasSize(2)))
                .andExpect(jsonPath("$.data.odemeler", hasSize(1)))
                .andExpect(jsonPath("$.data.bakiye").value(120.50));
    }

    @Test
    void bakiye_bosOgrenciSifir() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000000b0";
        long ogrenci = createStudent(tenant, "Bos", "13500000001");
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toplamTahakkuk").value(0.00))
                .andExpect(jsonPath("$.data.toplamOdeme").value(0.00))
                .andExpect(jsonPath("$.data.bakiye").value(0.00));
    }

    @Test
    void balance_baskaTenantOgrenci404() throws Exception {
        long ogrenciB = createStudent(TENANT_B, "OgrB", "13900000001");
        mockMvc.perform(get("/api/students/{id}/balance", ogrenciB).with(admin(TENANT_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 4. Tahsilat tahakkuga bagliyken farkli ogrenci -> 400 ---

    @Test
    void odemeBaskaOgrenciTahakkugu_400() throws Exception {
        String tenant = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        long ogr1 = createStudent(tenant, "O1", "14000000001");
        long ogr2 = createStudent(tenant, "O2", "14000000002");
        long accrual1 = createAccrual(tenant, ogr1, "100.00", null);

        // ogr2 ogr1'in tahakkugunu odemeye calisiyor -> 400.
        mockMvc.perform(post("/api/payments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogr2 + ",\"accrualId\":" + accrual1
                                + ",\"tutar\":50.00,\"odemeYontemi\":\"NAKIT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Dogru ogrenci ile -> 201.
        mockMvc.perform(post("/api/payments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogr1 + ",\"accrualId\":" + accrual1
                                + ",\"tutar\":50.00,\"odemeYontemi\":\"NAKIT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accrual.id").value(accrual1));
    }

    // --- 5. tutar <= 0 -> 400 (her uc entity, 0 ve negatif) ---

    @Test
    void tutarPozitifDegil_400() throws Exception {
        String tenant = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        long ogrenci = createStudent(tenant, "Tutar", "15000000001");

        for (String tutar : new String[] {"0", "-10.00"}) {
            mockMvc.perform(post("/api/accruals")
                            .with(admin(tenant))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ogrenciId\":" + ogrenci + ",\"tutar\":" + tutar + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            mockMvc.perform(post("/api/payments")
                            .with(admin(tenant))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ogrenciId\":" + ogrenci + ",\"tutar\":" + tutar
                                    + ",\"odemeYontemi\":\"NAKIT\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

            mockMvc.perform(post("/api/expenses")
                            .with(admin(tenant))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tutar\":" + tutar + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // --- 6. Zorunlu alan eksik -> 400 ---

    @Test
    void zorunluAlanEksik_400() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000000f6";
        long ogrenci = createStudent(tenant, "Eksik", "16000000001");

        // accrual: ogrenciId eksik.
        mockMvc.perform(post("/api/accruals")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutar\":100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // payment: odemeYontemi eksik.
        mockMvc.perform(post("/api/payments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"tutar\":50.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // expense: tutar eksik.
        mockMvc.perform(post("/api/expenses")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kategori\":\"Kira\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 7. Filtreler ---

    @Test
    void filtre_accrualOgrenciVeDonem() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000071";
        long ogr1 = createStudent(tenant, "F1", "17000000001");
        long ogr2 = createStudent(tenant, "F2", "17000000002");
        createAccrual(tenant, ogr1, "100.00", "2026-06");
        createAccrual(tenant, ogr1, "200.00", "2026-07");
        createAccrual(tenant, ogr2, "300.00", "2026-06");

        // ogr1 + donem 2026-06 -> 1.
        mockMvc.perform(get("/api/accruals")
                        .param("ogrenciId", String.valueOf(ogr1))
                        .param("donem", "2026-06")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].donem").value("2026-06"));
    }

    @Test
    void filtre_paymentTarihAraligiVeYontem() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000072";
        long ogrenci = createStudent(tenant, "FP", "17200000001");
        createPayment(tenant, ogrenci, "10.00", "NAKIT", "2026-06-01");
        createPayment(tenant, ogrenci, "20.00", "KART", "2026-06-15");
        createPayment(tenant, ogrenci, "30.00", "NAKIT", "2026-07-01");

        // 2026-06-01..2026-06-30 + NAKIT -> yalnizca ilki.
        mockMvc.perform(get("/api/payments")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("yontem", "NAKIT")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tutar").value(10.00));
    }

    @Test
    void filtre_expenseTarihAraligiVeKategori() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000073";
        createExpense(tenant, "100.00", "Kira Gideri", "2026-06-01");
        createExpense(tenant, "200.00", "Elektrik", "2026-06-15");
        createExpense(tenant, "300.00", "Kira Gideri", "2026-07-01");

        // 2026-06 araligi + "kira" (case-insensitive contains) -> yalnizca ilki.
        mockMvc.perform(get("/api/expenses")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30")
                        .param("kategori", "kira")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tutar").value(100.00));
    }

    // --- 8. YETKI (PARA HASSAS) ---

    @Test
    void yetki_frontdesk403_accounting201Ve200_teacher403() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000080";
        long ogrenci = createStudent(tenant, "Yetki", "18000000001");
        String accrualJson = "{\"ogrenciId\":" + ogrenci + ",\"tutar\":100.00}";
        String paymentJson = "{\"ogrenciId\":" + ogrenci + ",\"tutar\":50.00,\"odemeYontemi\":\"NAKIT\"}";
        String expenseJson = "{\"tutar\":75.00}";

        // FRONTDESK (para gormez) POST -> 403.
        mockMvc.perform(post("/api/accruals").with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content(accrualJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments").with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content(paymentJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/expenses").with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON).content(expenseJson))
                .andExpect(status().isForbidden());
        // FRONTDESK GET balance -> 403.
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());

        // TEACHER -> 403.
        mockMvc.perform(post("/api/accruals").with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(accrualJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());

        // FRONTDESK_ACCOUNTING POST -> 201.
        mockMvc.perform(post("/api/accruals").with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content(accrualJson))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payments").with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content(paymentJson))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/expenses").with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON).content(expenseJson))
                .andExpect(status().isCreated());
        // FRONTDESK_ACCOUNTING GET balance -> 200.
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci)
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());

        // ADMIN GET balance -> 200.
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(tenant)))
                .andExpect(status().isOk());
    }

    // --- 9. Mutlu yol: tenant_id sizmaz ---

    @Test
    void mutluYol_tenantIdSizmaz() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000090";
        long ogrenci = createStudent(tenant, "Mutlu", "19000000001");
        String body = mockMvc.perform(post("/api/accruals")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ogrenciId\":" + ogrenci + ",\"tutar\":100.00,\"donem\":\"2026-06\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tutar").value(100.00))
                .andExpect(jsonPath("$.data.ogrenci.id").value(ogrenci))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("tenant"));
    }
}
