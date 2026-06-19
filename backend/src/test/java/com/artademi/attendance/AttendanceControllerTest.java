package com.artademi.attendance;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Yoklama (Attendance) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. Tenant izolasyonu (PK-find + capraz-tenant grup/ogrenci/program sizinti yok),
 * otomatik giris uretimi, mukerrer oturum (409), toplu guncelleme, TEACHER erisim koprusu (kendi
 * grubu 201/200, baska grup 403, eslesmeyen sub 403), rol kapilari ve Bean Validation dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttendanceControllerTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // === JWT yardimcilari ===

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

    /** sub claim'i de iceren JWT (TEACHER erisim koprusu testleri icin). */
    private static RequestPostProcessor tokenWithSub(String tenantId, String sub, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("sub", sub)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private static RequestPostProcessor admin(String tenantId) {
        return token(tenantId, "ADMIN");
    }

    // === Referans olusturma yardimcilari (group/schedule testlerinden uyarlandi) ===

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
        return createTeacher(tenantId, ad, null);
    }

    /** keycloakUserId opsiyonel (TEACHER koprusu icin sub ile eslesir). */
    private long createTeacher(String tenantId, String ad, String keycloakUserId) throws Exception {
        StringBuilder json = new StringBuilder("{\"ad\":\"").append(ad)
                .append("\",\"soyad\":\"Hoca\",\"hakedisTipi\":\"SAATLIK\",")
                .append("\"saatlikUcret\":200.00,\"bransIds\":[]");
        if (keycloakUserId != null) {
            json.append(",\"keycloakUserId\":\"").append(keycloakUserId).append("\"");
        }
        json.append("}");
        String body = mockMvc.perform(post("/api/teachers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long createGroup(String tenantId, String ad, long bransId, long ogretmenId, long salonId)
            throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + bransId
                + ",\"ogretmenId\":" + ogretmenId + ",\"salonId\":" + salonId
                + ",\"aylikAidat\":500.00}";
        String body = mockMvc.perform(post("/api/groups")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** brans+ogretmen+salon ile GRUP olusturup grup id'sini doner. */
    private long createGrupWithRefs(String tenantId, String suffix) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long ogretmen = createTeacher(tenantId, "Ogretmen-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        return createGroup(tenantId, "Grup-" + suffix, brans, ogretmen, salon);
    }

    private long createStudent(String tenantId, String ad, String tc) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Ogrenci\",\"tcKimlikNo\":\"" + tc
                + "\",\"dogumTarihi\":\"2010-01-01\",\"yetiskinMi\":true}";
        String body = mockMvc.perform(post("/api/students")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long enroll(String tenantId, long ogrenciId, long grupId) throws Exception {
        String json = "{\"ogrenciId\":" + ogrenciId + ",\"grupId\":" + grupId + "}";
        String body = mockMvc.perform(post("/api/enrollments")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String sessionJson(long grupId, String tarih) {
        return "{\"grupId\":" + grupId + ",\"tarih\":\"" + tarih + "\"}";
    }

    private long createSession(String tenantId, long grupId, String tarih) throws Exception {
        String body = mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grupId, tarih)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    // === Testler ===

    @Test
    void tenantIzolasyonu_baskaTenantGoremezVe404() throws Exception {
        long grupA = createGrupWithRefs(TENANT_A, "izol");
        long idA = createSession(TENANT_A, grupA, "2026-03-01");

        // B baglaminda liste -> A'nin oturumu gorunmez.
        mockMvc.perform(get("/api/attendance-sessions").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/attendance-sessions/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantGrup404() throws Exception {
        long grupA = createGrupWithRefs(TENANT_A, "crossgrp");

        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grupA, "2026-03-02")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantProgram404_veYanlisGrupProgram400() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long brans = createBranch(tenant, "Brans-prog");
        long ogr = createTeacher(tenant, "Ogr-prog");
        long salon = createRoom(tenant, "Salon-prog");
        long grup1 = createGroup(tenant, "G1-prog", brans, ogr, salon);
        long grup2 = createGroup(tenant, "G2-prog", brans, ogr, salon);

        // grup2 icin bir program olustur.
        String schedJson = "{\"grupId\":" + grup2 + ",\"gun\":\"PAZARTESI\",\"baslangicSaati\":"
                + "\"10:00\",\"bitisSaati\":\"11:00\"}";
        String schedBody = mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long programGrup2 = objectMapper.readTree(schedBody).path("data").path("id").asLong();

        // grup1 oturumuna grup2'nin programi -> 400 (program bu gruba ait degil).
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grupId\":" + grup1 + ",\"tarih\":\"2026-03-03\",\"programId\":"
                                + programGrup2 + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Baska tenant'ta var olmayan program id -> 404.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grupId\":" + createGrupWithRefs(TENANT_B, "prog-b")
                                + ",\"tarih\":\"2026-03-03\",\"programId\":" + programGrup2 + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void oturumOlustur_aktifOgrencilerIcinGirisUretir_GELMEDI() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long grup = createGrupWithRefs(tenant, "auto");
        long ogr = createStudent(tenant, "Ali", "10000000001");
        enroll(tenant, ogr, grup);

        long id = createSession(tenant, grup, "2026-03-04");

        mockMvc.perform(get("/api/attendance-sessions/{id}", id).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries", hasSize(1)))
                .andExpect(jsonPath("$.data.entries[0].ogrenci.id").value(ogr))
                .andExpect(jsonPath("$.data.entries[0].durum").value("GELMEDI"));
    }

    @Test
    void aktifOgrenciYok_oturumGirissizOlusur() throws Exception {
        String tenant = "55555555-5555-5555-5555-555555555555";
        long grup = createGrupWithRefs(tenant, "empty");
        long id = createSession(tenant, grup, "2026-03-05");

        mockMvc.perform(get("/api/attendance-sessions/{id}", id).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries", hasSize(0)));
    }

    @Test
    void mukerrerOturum_ayniGrupTarih_409() throws Exception {
        String tenant = "66666666-6666-6666-6666-666666666666";
        long grup = createGrupWithRefs(tenant, "dup");
        createSession(tenant, grup, "2026-03-06");

        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grup, "2026-03-06")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void topluGuncelleme_GELDI_veGETgosterir() throws Exception {
        String tenant = "77777777-7777-7777-7777-777777777777";
        long grup = createGrupWithRefs(tenant, "bulk");
        long ogr = createStudent(tenant, "Veli", "10000000002");
        enroll(tenant, ogr, grup);
        long id = createSession(tenant, grup, "2026-03-07");

        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogr + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].durum").value("GELDI"));

        mockMvc.perform(get("/api/attendance-sessions/{id}", id).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].durum").value("GELDI"));
    }

    @Test
    void topluGuncelleme_yabanciOgrenci_404() throws Exception {
        String tenant = "88888888-8888-8888-8888-888888888888";
        long grup = createGrupWithRefs(tenant, "foreign");
        long id = createSession(tenant, grup, "2026-03-08");
        // Gruba kayitli olmayan ogrenci.
        long yabanci = createStudent(tenant, "Yabanci", "10000000003");

        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + yabanci + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void teacherKoprusu_kendiGrubu201ve200_baskaGrup403_eslesmeyenSub403() throws Exception {
        String tenant = "99999999-9999-9999-9999-999999999999";
        String subA = "sub-teacher-a";
        String subB = "sub-teacher-b";

        long brans = createBranch(tenant, "Brans-tch");
        long salon = createRoom(tenant, "Salon-tch");
        long ogrA = createTeacher(tenant, "OgretmenA", subA);
        long ogrB = createTeacher(tenant, "OgretmenB", subB);
        long grupA = createGroup(tenant, "GrupA-tch", brans, ogrA, salon);
        long grupB = createGroup(tenant, "GrupB-tch", brans, ogrB, salon);

        // TEACHER (subA) kendi grubuna oturum -> 201.
        String body = mockMvc.perform(post("/api/attendance-sessions")
                        .with(tokenWithSub(tenant, subA, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grupA, "2026-03-09")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long sessionA = objectMapper.readTree(body).path("data").path("id").asLong();

        // TEACHER (subA) kendi oturumunu GET -> 200.
        mockMvc.perform(get("/api/attendance-sessions/{id}", sessionA)
                        .with(tokenWithSub(tenant, subA, "TEACHER")))
                .andExpect(status().isOk());

        // TEACHER (subA) BASKA ogretmenin grubuna oturum -> 403.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(tokenWithSub(tenant, subA, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grupB, "2026-03-10")))
                .andExpect(status().isForbidden());

        // Hicbir ogretmenle eslesmeyen sub -> 403.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(tokenWithSub(tenant, "sub-nobody", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grupA, "2026-03-11")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherListe_yalnizcaKendiGruplari() throws Exception {
        String tenant = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String subA = "list-teacher-a";
        long brans = createBranch(tenant, "Brans-list");
        long salon = createRoom(tenant, "Salon-list");
        long ogrA = createTeacher(tenant, "ListA", subA);
        long ogrB = createTeacher(tenant, "ListB");
        long grupA = createGroup(tenant, "ListGrupA", brans, ogrA, salon);
        long grupB = createGroup(tenant, "ListGrupB", brans, ogrB, salon);

        long sA = createSession(tenant, grupA, "2026-03-12");
        createSession(tenant, grupB, "2026-03-13");

        // TEACHER (subA) liste -> yalnizca kendi grubunun oturumu.
        mockMvc.perform(get("/api/attendance-sessions").with(tokenWithSub(tenant, subA, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(sA));
    }

    @Test
    void yetki_adminCreate201_frontdeskCreate201_accountingCreate403_accountingGet200() throws Exception {
        String tenant = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        long grup1 = createGrupWithRefs(tenant, "role1");
        long grup2 = createGrupWithRefs(tenant, "role2");

        // ADMIN create -> 201.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(token(tenant, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grup1, "2026-03-14")))
                .andExpect(status().isCreated());

        // FRONTDESK create -> 201.
        long fdSession = objectMapper.readTree(mockMvc.perform(post("/api/attendance-sessions")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grup2, "2026-03-15")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        // FRONTDESK_ACCOUNTING create -> 403 (yazma yetkisi yok).
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(grup1, "2026-03-16")))
                .andExpect(status().isForbidden());

        // FRONTDESK_ACCOUNTING GET -> 200 (okuma yetkisi var).
        mockMvc.perform(get("/api/attendance-sessions/{id}", fdSession)
                        .with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());
    }

    @Test
    void validation_grupIdVeyaTarihEksik_400() throws Exception {
        String tenant = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        long grup = createGrupWithRefs(tenant, "valid");

        // grupId eksik.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tarih\":\"2026-03-17\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // tarih eksik.
        mockMvc.perform(post("/api/attendance-sessions")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grupId\":" + grup + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void caprazTenantOgrenci_topluGuncellemede404() throws Exception {
        // A'da grup+oturum; B'nin ogrencisi A oturumunda gorunmez -> 404 (sizinti yok).
        long grupA = createGrupWithRefs(TENANT_A, "crossogr");
        long idA = createSession(TENANT_A, grupA, "2026-03-18");
        long ogrB = createStudent(TENANT_B, "BogrenciX", "10000000099");

        mockMvc.perform(put("/api/attendance-sessions/{id}/entries", idA)
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"ogrenciId\":" + ogrB + ",\"durum\":\"GELDI\"}]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void grupYardimciUcu_tarihAraligiSirali() throws Exception {
        String tenant = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        long grup = createGrupWithRefs(tenant, "helper");
        createSession(tenant, grup, "2026-04-03");
        createSession(tenant, grup, "2026-04-01");
        createSession(tenant, grup, "2026-04-02");

        // Tum aralik -> tarihe gore artan.
        mockMvc.perform(get("/api/groups/{id}/attendance-sessions", grup).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].tarih").value("2026-04-01"))
                .andExpect(jsonPath("$.data[2].tarih").value("2026-04-03"));

        // from/to filtresi.
        mockMvc.perform(get("/api/groups/{id}/attendance-sessions", grup)
                        .param("from", "2026-04-02")
                        .param("to", "2026-04-02")
                        .with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tarih").value("2026-04-02"));
    }
}
