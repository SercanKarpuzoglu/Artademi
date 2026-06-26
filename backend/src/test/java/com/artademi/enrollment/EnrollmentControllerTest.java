package com.artademi.enrollment;

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
 * Kayit (Enrollment) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. Tenant izolasyonu (PK-find + capraz-tenant ogrenci/grup sizinti yok), statu
 * kurali (PASIF -> 400 VALIDATION_ERROR), mukerrer aktif kayit (-> 409), leave akisi (AYRILDI +
 * tekrar yazilabilirlik), durum/grup filtresi ve yetki dogrular.
 *
 * <p>Veri ADMIN token ile API uzerinden olusturulur; tenant her istekte tenant_id claim'inden gelir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollmentControllerTest {

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
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}";
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

    private String enrollJson(long ogrenciId, long grupId) {
        return "{\"ogrenciId\":" + ogrenciId + ",\"grupId\":" + grupId + "}";
    }

    private long createEnrollment(String tenantId, long ogrenciId, long grupId) throws Exception {
        String body = mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenciId, grupId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durum").value("AKTIF"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // --- 1. Tenant izolasyonu ---

    @Test
    void tenantIzolasyonu_baskaTenantGoremezVe404() throws Exception {
        long grup = createGroup(TENANT_A, "izol");
        long ogrenci = createStudent(TENANT_A, "Izol", "10000000001");
        long idA = createEnrollment(TENANT_A, ogrenci, grup);

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/enrollments").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/enrollments/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 2. Capraz-tenant referans (ikisi ayri) ---

    @Test
    void caprazTenant_baskaTenantOgrenci404() throws Exception {
        long ogrenciB = createStudent(TENANT_B, "OgrB", "20000000001");
        long grupA = createGroup(TENANT_A, "capraz-ogr");

        // A'da, B'nin ogrenci id'siyle kayit -> 404 (sizinti yok).
        mockMvc.perform(post("/api/enrollments")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenciB, grupA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenant_baskaTenantGrup404() throws Exception {
        long grupB = createGroup(TENANT_B, "capraz-grup");
        long ogrenciA = createStudent(TENANT_A, "OgrA", "30000000001");

        // A'da, B'nin grup id'siyle kayit -> 404 (sizinti yok).
        mockMvc.perform(post("/api/enrollments")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenciA, grupB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // --- 3. Mutlu yol ---

    @Test
    void mutluYol_deneme201VeAktif() throws Exception {
        String tenant = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        long grup = createGroup(tenant, "mutlu");
        long ogrenci = createStudent(tenant, "Mutlu", "40000000001");

        String body = mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenci, grup)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durum").value("AKTIF"))
                .andExpect(jsonPath("$.data.kayitTarihi").exists())
                .andExpect(jsonPath("$.data.ogrenci.id").value(ogrenci))
                .andExpect(jsonPath("$.data.grup.id").value(grup))
                .andExpect(jsonPath("$.data.grup.tip").value("GRUP"))
                .andReturn().getResponse().getContentAsString();
        // tenant_id sizdirilmaz.
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("tenant"));
    }

    // --- 4. Mukerrer aktif kayit -> 409 ---

    @Test
    void mukerrerAktifKayit_409() throws Exception {
        String tenant = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        long grup = createGroup(tenant, "mukerrer");
        long ogrenci = createStudent(tenant, "Muk", "50000000001");
        createEnrollment(tenant, ogrenci, grup);

        mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenci, grup)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // --- 5. PASIF ogrenci -> 400 VALIDATION_ERROR ---

    @Test
    void pasifOgrenci_400() throws Exception {
        String tenant = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        long grup = createGroup(tenant, "pasif");
        long ogrenci = createStudent(tenant, "Pasif", "60000000001");

        mockMvc.perform(patch("/api/students/{id}/status", ogrenci)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PASIF\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenci, grup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- 6. leave: AYRILDI + tekrar yazilabilir ---

    @Test
    void leave_ayrildiSonraTekrarKayit201() throws Exception {
        String tenant = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        long grup = createGroup(tenant, "leave");
        long ogrenci = createStudent(tenant, "Leave", "70000000001");
        long id = createEnrollment(tenant, ogrenci, grup);

        mockMvc.perform(patch("/api/enrollments/{id}/leave", id).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("AYRILDI"))
                .andExpect(jsonPath("$.data.ayrilmaTarihi").exists());

        // Artik mukerrer degil: ayni ogrenci+grup icin yeni AKTIF kayit acilabilir.
        mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollJson(ogrenci, grup)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durum").value("AKTIF"));
    }

    // --- 7. Filtre: grupId + durum ---

    @Test
    void filtre_grupVeDurum() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000007";
        long grup1 = createGroup(tenant, "f1");
        long grup2 = createGroup(tenant, "f2");
        long ogr1 = createStudent(tenant, "F1", "80000000001");
        long ogr2 = createStudent(tenant, "F2", "80000000002");

        createEnrollment(tenant, ogr1, grup1);
        long leaveId = createEnrollment(tenant, ogr2, grup1);
        createEnrollment(tenant, ogr1, grup2);

        // grup1'i ayrilanli + aktif karisik yapalim.
        mockMvc.perform(patch("/api/enrollments/{id}/leave", leaveId).with(admin(tenant)))
                .andExpect(status().isOk());

        // grup1 + AKTIF -> yalnizca 1 (ogr1).
        mockMvc.perform(get("/api/enrollments")
                        .param("grupId", String.valueOf(grup1))
                        .param("durum", "AKTIF")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].ogrenci.id").value(ogr1));

        // Nested uc: grup1'in tum kayitlari -> 2.
        mockMvc.perform(get("/api/groups/{id}/enrollments", grup1).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // Nested uc: ogr1'in tum kayitlari -> 2 (grup1 + grup2).
        mockMvc.perform(get("/api/students/{id}/enrollments", ogr1).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // --- 8. Yetki ---

    @Test
    void yetki_teacher403_frontdesk201Ve200() throws Exception {
        String tenant = "00000000-0000-0000-0000-000000000008";
        long grup = createGroup(tenant, "yetki");
        long ogrenci = createStudent(tenant, "Yetki", "90000000001");
        String json = enrollJson(ogrenci, grup);

        // TEACHER POST -> 403.
        mockMvc.perform(post("/api/enrollments")
                        .with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // FRONTDESK POST -> 201.
        mockMvc.perform(post("/api/enrollments")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // FRONTDESK GET -> 200.
        mockMvc.perform(get("/api/enrollments").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isOk());
    }

    // --- Grup transferi (İş A) ---

    private static final String DONEM = "2026-03";

    /** GRUP tipi grup, verilen aidatla. */
    private long createGroupAidat(String tenantId, String suffix, String aidat) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long ogretmen = createTeacher(tenantId, "Ogretmen-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        String json = "{\"ad\":\"Grup-" + suffix + "\",\"tip\":\"GRUP\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon + ",\"aylikAidat\":" + aidat + "}";
        String body = mockMvc.perform(post("/api/groups").with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** OZEL tipi grup (dersBasiUcret). */
    private long createOzelGroup(String tenantId, String suffix) throws Exception {
        long brans = createBranch(tenantId, "OBrans-" + suffix);
        long ogretmen = createTeacher(tenantId, "OOgretmen-" + suffix);
        String json = "{\"ad\":\"Ozel-" + suffix + "\",\"tip\":\"OZEL\",\"bransId\":" + brans
                + ",\"ogretmenId\":" + ogretmen + ",\"dersBasiUcret\":300.00}";
        String body = mockMvc.perform(post("/api/groups").with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void createAccrual(String tenantId, long ogrenciId, long grupId, String tutar) throws Exception {
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"grupId\":" + grupId + ",\"donem\":\"" + DONEM
                + "\",\"tutar\":" + tutar + ",\"aciklama\":\"test tahakkuk\"}";
        mockMvc.perform(post("/api/accruals").with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private String transferJson(long yeniGrupId) {
        return "{\"yeniGrupId\":" + yeniGrupId + ",\"donem\":\"" + DONEM + "\"}";
    }

    @Test
    void transfer_happyPath_iadeVeEk_bakiyeDogru() throws Exception {
        String tenant = "11111111-aaaa-aaaa-aaaa-111111111111";
        long eski = createGroupAidat(tenant, "eski", "500.00");
        long yeni = createGroupAidat(tenant, "yeni", "800.00");
        long ogrenci = createStudent(tenant, "Trans", "11111111101");
        long enrId = createEnrollment(tenant, ogrenci, eski);
        // O dönem eski grup tahakkuku üretilmiş (500)
        createAccrual(tenant, ogrenci, eski, "500.00");

        // Transfer -> 200, yeni AKTIF kayıt, yeni grup
        String body = mockMvc.perform(post("/api/enrollments/{id}/transfer", enrId).with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(yeni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("AKTIF"))
                .andExpect(jsonPath("$.data.grup.id").value((int) yeni))
                .andReturn().getResponse().getContentAsString();
        long yeniEnr = objectMapper.readTree(body).path("data").path("id").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(enrId, yeniEnr);

        // Eski kayıt AYRILDI
        mockMvc.perform(get("/api/enrollments/{id}", enrId).with(admin(tenant)))
                .andExpect(jsonPath("$.data.durum").value("AYRILDI"));

        // Bakiye = 500 (eski) − 500 (iade) + 800 (yeni) = 800
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bakiye").value(800.00));
    }

    @Test
    void transfer_donemTahakkukYok_iadeUretilmez() throws Exception {
        String tenant = "22222222-aaaa-aaaa-aaaa-222222222222";
        long eski = createGroupAidat(tenant, "eski2", "500.00");
        long yeni = createGroupAidat(tenant, "yeni2", "800.00");
        long ogrenci = createStudent(tenant, "Trans2", "22222222101");
        long enrId = createEnrollment(tenant, ogrenci, eski);
        // Tahakkuk YOK

        mockMvc.perform(post("/api/enrollments/{id}/transfer", enrId).with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(yeni)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durum").value("AKTIF"));

        // Hiç tahakkuk üretilmedi -> bakiye 0
        mockMvc.perform(get("/api/students/{id}/balance", ogrenci).with(admin(tenant)))
                .andExpect(jsonPath("$.data.bakiye").value(0.00));
    }

    @Test
    void transfer_ozelGrup_400() throws Exception {
        String tenant = "33333333-aaaa-aaaa-aaaa-333333333333";
        long eski = createGroupAidat(tenant, "eski3", "500.00");
        long ozel = createOzelGroup(tenant, "ozel3");
        long ogrenci = createStudent(tenant, "Trans3", "33333333101");
        long enrId = createEnrollment(tenant, ogrenci, eski);

        mockMvc.perform(post("/api/enrollments/{id}/transfer", enrId).with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(ozel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void transfer_caprazTenant_404() throws Exception {
        String tenant = "44444444-aaaa-aaaa-aaaa-444444444444";
        String tenantB = "44444444-bbbb-bbbb-bbbb-444444444444";
        long eski = createGroupAidat(tenant, "eski4", "500.00");
        long yeniB = createGroupAidat(tenantB, "yeni4b", "800.00");
        long ogrenci = createStudent(tenant, "Trans4", "44444444101");
        long enrId = createEnrollment(tenant, ogrenci, eski);

        mockMvc.perform(post("/api/enrollments/{id}/transfer", enrId).with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(yeniB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void transfer_zatenAktifGrup_409() throws Exception {
        String tenant = "55555555-aaaa-aaaa-aaaa-555555555555";
        long g1 = createGroupAidat(tenant, "g1", "500.00");
        long g2 = createGroupAidat(tenant, "g2", "800.00");
        long ogrenci = createStudent(tenant, "Trans5", "55555555101");
        long enr1 = createEnrollment(tenant, ogrenci, g1);
        createEnrollment(tenant, ogrenci, g2); // zaten g2'de aktif

        mockMvc.perform(post("/api/enrollments/{id}/transfer", enr1).with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(g2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void transfer_teacher_403() throws Exception {
        String tenant = "66666666-aaaa-aaaa-aaaa-666666666666";
        long eski = createGroupAidat(tenant, "eski6", "500.00");
        long yeni = createGroupAidat(tenant, "yeni6", "800.00");
        long ogrenci = createStudent(tenant, "Trans6", "66666666101");
        long enrId = createEnrollment(tenant, ogrenci, eski);

        mockMvc.perform(post("/api/enrollments/{id}/transfer", enrId).with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(yeni)))
                .andExpect(status().isForbidden());
    }
}
