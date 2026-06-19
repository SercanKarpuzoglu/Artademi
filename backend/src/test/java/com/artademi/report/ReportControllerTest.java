package com.artademi.report;

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
 * Rapor (RAPOR) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. SALT OKUNUR raporlarin dogrulugunu (BigDecimal scale 2, net = gelir - gider),
 * TENANT IZOLASYONUNU (A'nin raporlari B'nin verisini icermez) ve YETKI MATRISINI dogrular.
 *
 * <p>Veri ADMIN token ile gercek yazma uclari uzerinden olusturulur; tenant her istekte tenant_id
 * claim'inden gelir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportControllerTest {

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

    // --- Yardimci: referans/is verisi olusturma ---

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

    /** SAATLIK ogretmen. */
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

    /** CIRO_ORANI ogretmen (donem tahsilatlarindan hakedis uretebilmek icin). */
    private long createCiroTeacher(String tenantId, String ad, String oran) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Ciro\",\"hakedisTipi\":\"CIRO_ORANI\","
                + "\"ciroOrani\":" + oran + ",\"bransIds\":[]}";
        String body = mockMvc.perform(post("/api/teachers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

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

    private void createAccrual(String tenantId, long ogrenciId, String tutar, String donem)
            throws Exception {
        String donemJson = donem == null ? "" : ",\"donem\":\"" + donem + "\"";
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"tutar\":" + tutar + donemJson + "}";
        mockMvc.perform(post("/api/accruals")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private void createPayment(String tenantId, long ogrenciId, Long grupId, String tutar,
            String odemeTarihi) throws Exception {
        String grupJson = grupId == null ? "" : ",\"grupId\":" + grupId;
        String json = "{\"ogrenciId\":" + ogrenciId + grupJson + ",\"tutar\":" + tutar
                + ",\"odemeYontemi\":\"NAKIT\",\"odemeTarihi\":\"" + odemeTarihi + "\"}";
        mockMvc.perform(post("/api/payments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private void createExpense(String tenantId, String tutar, String giderTarihi) throws Exception {
        String json = "{\"tutar\":" + tutar + ",\"kategori\":\"Kira\",\"giderTarihi\":\""
                + giderTarihi + "\"}";
        mockMvc.perform(post("/api/expenses")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private long createProduct(String tenantId, String ad, String fiyat) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"satisFiyati\":" + fiyat + ",\"stokAdedi\":1000}";
        String body = mockMvc.perform(post("/api/products")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void createSale(String tenantId, long urunId, int adet, String satisTarihi)
            throws Exception {
        String json = "{\"urunId\":" + urunId + ",\"adet\":" + adet + ",\"satisTarihi\":\""
                + satisTarihi + "\"}";
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private long createEnrollment(String tenantId, long ogrenciId, long grupId) throws Exception {
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"grupId\":" + grupId + "}";
        String body = mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void leaveEnrollment(String tenantId, long enrollmentId) throws Exception {
        mockMvc.perform(patch("/api/enrollments/{id}/leave", enrollmentId).with(admin(tenantId)))
                .andExpect(status().isOk());
    }

    private long hesaplaPayout(String tenantId, long ogretmenId, String donem) throws Exception {
        String json = "{\"ogretmenId\":" + ogretmenId + ",\"donem\":\"" + donem + "\"}";
        String body = mockMvc.perform(post("/api/payouts/hesapla")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // --- 1. financial-summary: dogruluk (KRITIK net = gelir - gider) ---

    @Test
    void financialSummary_kalemlerVeNetDogru() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000101";
        String donem = "2026-06";

        // CIRO_ORANI ogretmen + grup; gruba bagli tahsilat hakedis uretsin (oran %10, KDV varsayilan 20).
        long ogretmen = createCiroTeacher(tenant, "Ciro", "10.00");
        long grup = createGroup(tenant, "fs", ogretmen);
        long ogrenci = createStudent(tenant, "FS", "21000000001");
        createEnrollment(tenant, ogrenci, grup);

        // Tahsilat: gruba bagli 1100.00 (donem icinde) -> hem gelir hem hakedis tabani.
        createPayment(tenant, ogrenci, grup, "1100.00", "2026-06-15");
        // Urun satis: 2 adet x 150 = 300.00.
        long urun = createProduct(tenant, "Defter", "150.00");
        createSale(tenant, urun, 2, "2026-06-10");
        // Ofis gideri: 400.00.
        createExpense(tenant, "400.00", "2026-06-05");
        // Hakedis (donem): netCiro = 1100 / 1.20 = 916.67; hakedis = 916.67 * 0.10 = 91.67.
        hesaplaPayout(tenant, ogretmen, donem);

        // gelir: tahsilat 1100.00 + urunSatis 300.00 = 1400.00
        // gider: ofisGideri 400.00 + hakedis 91.67 = 491.67
        // net = 1400.00 - 491.67 = 908.33
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", donem)
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.donem").value(donem))
                .andExpect(jsonPath("$.data.gelir.tahsilat").value(1100.00))
                .andExpect(jsonPath("$.data.gelir.urunSatis").value(300.00))
                .andExpect(jsonPath("$.data.gelir.toplamGelir").value(1400.00))
                .andExpect(jsonPath("$.data.gider.ofisGideri").value(400.00))
                .andExpect(jsonPath("$.data.gider.hakedis").value(91.67))
                .andExpect(jsonPath("$.data.gider.toplamGider").value(491.67))
                .andExpect(jsonPath("$.data.net").value(908.33));
    }

    @Test
    void financialSummary_bosDonemSifir() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000102";
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gelir.toplamGelir").value(0.00))
                .andExpect(jsonPath("$.data.gider.toplamGider").value(0.00))
                .andExpect(jsonPath("$.data.net").value(0.00));
    }

    @Test
    void financialSummary_gecersizDonem400() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000103";
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026/06")
                        .with(admin(tenant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 2. financial-summary: TENANT IZOLASYONU ---

    @Test
    void financialSummary_tenantIzolasyonu() throws Exception {
        // B'de tahsilat var; A'nin ozeti B'yi gormez (0).
        long ogrenciB = createStudent(TENANT_B, "IzolB", "22000000001");
        createPayment(TENANT_B, ogrenciB, null, "777.00", "2026-06-01");

        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gelir.tahsilat").value(0.00));
    }

    // --- 3. student-balances: dogruluk, sadeceBorclu, siralama ---

    @Test
    void studentBalances_bakiyeBorcluFiltreVeSiralama() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000104";
        // Cok borclu: tahakkuk 300, odeme 50 -> bakiye 250.
        long cokBorclu = createStudent(tenant, "CokBorclu", "23000000001");
        createAccrual(tenant, cokBorclu, "300.00", "2026-06");
        createPayment(tenant, cokBorclu, null, "50.00", "2026-06-01");
        // Az borclu: tahakkuk 100, odeme 0 -> bakiye 100.
        long azBorclu = createStudent(tenant, "AzBorclu", "23000000002");
        createAccrual(tenant, azBorclu, "100.00", "2026-06");
        // Borcsuz: tahakkuk 80, odeme 80 -> bakiye 0 (sadeceBorclu disi).
        long borcsuz = createStudent(tenant, "Borcsuz", "23000000003");
        createAccrual(tenant, borcsuz, "80.00", "2026-06");
        createPayment(tenant, borcsuz, null, "80.00", "2026-06-01");

        // sadeceBorclu=true -> yalnizca 2 borclu, bakiye DESC.
        mockMvc.perform(get("/api/reports/student-balances").param("sadeceBorclu", "true")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].ogrenciId").value(cokBorclu))
                .andExpect(jsonPath("$.data[0].toplamTahakkuk").value(300.00))
                .andExpect(jsonPath("$.data[0].toplamOdeme").value(50.00))
                .andExpect(jsonPath("$.data[0].bakiye").value(250.00))
                .andExpect(jsonPath("$.data[1].ogrenciId").value(azBorclu))
                .andExpect(jsonPath("$.data[1].bakiye").value(100.00));

        // sadeceBorclu=false (varsayilan) -> ucu de.
        mockMvc.perform(get("/api/reports/student-balances").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void studentBalances_tenantIzolasyonu() throws Exception {
        // B'de borclu ogrenci; A'nin raporu yalnizca A'nin ogrencisini icerir.
        long ogrenciB = createStudent(TENANT_B, "BalB", "23900000001");
        createAccrual(TENANT_B, ogrenciB, "500.00", "2026-06");

        String tenantA = "00000000-0000-0000-0000-000000000105";
        long ogrenciA = createStudent(tenantA, "BalA", "23900000002");
        createAccrual(tenantA, ogrenciA, "120.00", "2026-06");

        mockMvc.perform(get("/api/reports/student-balances").with(admin(tenantA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].ogrenciId").value(ogrenciA))
                .andExpect(jsonPath("$.data[0].bakiye").value(120.00));
    }

    // --- 4. teacher-payouts: aggregate ---

    @Test
    void teacherPayouts_donemAggregate() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000106";
        String donem = "2026-06";
        // SAATLIK ogretmen, oturum yok -> hesaplananTutar 0.00 (yine de kalem doner, toplam 0).
        long ogretmen = createTeacher(tenant, "Pay");
        hesaplaPayout(tenant, ogretmen, donem);

        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", donem)
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.donem").value(donem))
                .andExpect(jsonPath("$.data.kalemler", hasSize(1)))
                .andExpect(jsonPath("$.data.kalemler[0].ogretmenId").value(ogretmen))
                .andExpect(jsonPath("$.data.kalemler[0].hakedisTipi").value("SAATLIK"))
                .andExpect(jsonPath("$.data.kalemler[0].durum").value("HESAPLANDI"))
                .andExpect(jsonPath("$.data.toplamHakedis").value(0.00));
    }

    @Test
    void teacherPayouts_ciroToplamDogru() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000107";
        String donem = "2026-06";
        long ogretmen = createCiroTeacher(tenant, "CiroPay", "10.00");
        long grup = createGroup(tenant, "tp", ogretmen);
        long ogrenci = createStudent(tenant, "TP", "24000000001");
        createEnrollment(tenant, ogrenci, grup);
        createPayment(tenant, ogrenci, grup, "1100.00", "2026-06-15");
        hesaplaPayout(tenant, ogretmen, donem);

        // netCiro = 1100/1.20 = 916.67; hakedis = 91.67.
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", donem)
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kalemler", hasSize(1)))
                .andExpect(jsonPath("$.data.kalemler[0].hesaplananTutar").value(91.67))
                .andExpect(jsonPath("$.data.toplamHakedis").value(91.67));
    }

    @Test
    void teacherPayouts_gecersizDonem400() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000108";
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", "2026/06")
                        .with(admin(tenant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 5. group-occupancy: yalnizca AKTIF kayit sayilir ---

    @Test
    void groupOccupancy_yalnizcaAktifKayitSayilir() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000109";
        long ogretmen = createTeacher(tenant, "GO");
        long grup = createGroup(tenant, "go", ogretmen);
        long ogr1 = createStudent(tenant, "GO1", "25000000001");
        long ogr2 = createStudent(tenant, "GO2", "25000000002");
        createEnrollment(tenant, ogr1, grup); // AKTIF
        long ayrilan = createEnrollment(tenant, ogr2, grup);
        leaveEnrollment(tenant, ayrilan); // AYRILDI

        mockMvc.perform(get("/api/reports/group-occupancy").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].grupId").value(grup))
                .andExpect(jsonPath("$.data[0].ogretmenAd").value("GO Hoca"))
                .andExpect(jsonPath("$.data[0].aktifOgrenciSayisi").value(1));
    }

    @Test
    void groupOccupancy_aktifMiFiltresi() throws Exception {
        String tenant = "00000000-0000-0000-0000-00000000010a";
        long ogretmen = createTeacher(tenant, "GF");
        createGroup(tenant, "gf", ogretmen);

        // aktifMi=false -> aktif grup oldugundan bos.
        mockMvc.perform(get("/api/reports/group-occupancy").param("aktifMi", "false")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // aktifMi=true -> 1 grup.
        mockMvc.perform(get("/api/reports/group-occupancy").param("aktifMi", "true")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void groupOccupancy_tenantIzolasyonu() throws Exception {
        long ogretmenB = createTeacher(TENANT_B, "GOB");
        createGroup(TENANT_B, "gob", ogretmenB);

        String tenantA = "00000000-0000-0000-0000-00000000010b";
        long ogretmenA = createTeacher(tenantA, "GOA");
        long grupA = createGroup(tenantA, "goa", ogretmenA);

        mockMvc.perform(get("/api/reports/group-occupancy").with(admin(tenantA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].grupId").value(grupA));
    }

    // --- 6. YETKI MATRISI ---

    @Test
    void yetki_financialSummary() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000001c1";
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(token(tenant, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/financial-summary").param("donem", "2026-06")
                        .with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void yetki_teacherPayouts() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000001c2";
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", "2026-06")
                        .with(token(tenant, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", "2026-06")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", "2026-06")
                        .with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/teacher-payouts").param("donem", "2026-06")
                        .with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void yetki_studentBalances() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000001c3";
        mockMvc.perform(get("/api/reports/student-balances").with(token(tenant, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/student-balances")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/student-balances").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/student-balances").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void yetki_groupOccupancy() throws Exception {
        String tenant = "00000000-0000-0000-0000-0000000001c4";
        mockMvc.perform(get("/api/reports/group-occupancy").with(token(tenant, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/group-occupancy")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/group-occupancy").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/group-occupancy").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
