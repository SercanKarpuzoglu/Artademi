package com.artademi.schedule;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Program (Schedule) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. Tenant izolasyonu (PK-find + capraz-tenant grup sizinti yok),
 * @SaatAraligiGecerli (bitis>baslangic) validasyonu, salon/ogretmen cakismasi (409), saat
 * ortusmemesi, OZEL grup salon-atlama, Specification filtreleri, aktif filtresi/PATCH active,
 * grup yardimci ucu ve yetki dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScheduleControllerTest {

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

    // === Referans olusturma yardimcilari (group testinden kopya) ===

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

    private String grupJson(String ad, long bransId, long ogretmenId, long salonId) {
        return "{\"ad\":\"" + ad + "\",\"tip\":\"GRUP\",\"bransId\":" + bransId
                + ",\"ogretmenId\":" + ogretmenId + ",\"salonId\":" + salonId
                + ",\"aylikAidat\":500.00}";
    }

    private String ozelJson(String ad, long bransId, long ogretmenId) {
        return "{\"ad\":\"" + ad + "\",\"tip\":\"OZEL\",\"bransId\":" + bransId
                + ",\"ogretmenId\":" + ogretmenId + ",\"dersBasiUcret\":300.00}";
    }

    private long createGroup(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/groups")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** Tek brans+ogretmen+salon ile GRUP olusturup grup id'sini doner. */
    private long createGrupWithRefs(String tenantId, String suffix) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long ogretmen = createTeacher(tenantId, "Ogretmen-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        return createGroup(tenantId, grupJson("Grup-" + suffix, brans, ogretmen, salon));
    }

    // === Program olusturma yardimcilari ===

    private String scheduleJson(long grupId, String gun, String baslangic, String bitis) {
        return "{\"grupId\":" + grupId + ",\"gun\":\"" + gun + "\",\"baslangicSaati\":\""
                + baslangic + "\",\"bitisSaati\":\"" + bitis + "\"}";
    }

    private long createSchedule(String tenantId, String json) throws Exception {
        String body = mockMvc.perform(post("/api/schedules")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aktif").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    @Test
    void tenantIzolasyonu_baskaTenantGoremezVe404() throws Exception {
        long grupA = createGrupWithRefs(TENANT_A, "izol");
        long idA = createSchedule(TENANT_A, scheduleJson(grupA, "PAZARTESI", "10:00", "11:00"));

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/schedules").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/schedules/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantGrup404() throws Exception {
        // A'da grup; B baglaminda o grup id'siyle program -> 404 (sizinti yok).
        long grupA = createGrupWithRefs(TENANT_A, "crossgrp");

        mockMvc.perform(post("/api/schedules")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grupA, "SALI", "09:00", "10:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void mutluYol_gecerliProgram201_grupOzetiDolu() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long grup = createGrupWithRefs(tenant, "happy");

        String body = mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup, "CUMARTESI", "11:00", "12:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gun").value("CUMARTESI"))
                .andExpect(jsonPath("$.data.baslangicSaati").value("11:00:00"))
                .andExpect(jsonPath("$.data.bitisSaati").value("12:00:00"))
                .andExpect(jsonPath("$.data.grup.id").value(grup))
                .andExpect(jsonPath("$.data.grup.tip").value("GRUP"))
                .andExpect(jsonPath("$.data.salon.id").exists())
                .andExpect(jsonPath("$.data.ogretmen.id").exists())
                .andReturn().getResponse().getContentAsString();
        // tenant_id sizdirilmaz.
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("tenant"));
    }

    @Test
    void bitisBaslangictanKucukEsit_400_bitisSaatiAlani() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long grup = createGrupWithRefs(tenant, "saat");

        mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup, "PAZARTESI", "12:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.bitisSaati").exists());

        // Esit saatler de gecersiz.
        mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup, "PAZARTESI", "11:00", "11:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.bitisSaati").exists());
    }

    @Test
    void salonCakismasi_ayniSalonOrtusenSaat_409() throws Exception {
        String tenant = "55555555-5555-5555-5555-555555555555";
        // Tek salon, iki farkli grup ayni salonu paylasir (farkli ogretmen ki ogretmen cakismasi
        // ilk tetiklenmesin diye degil — yine de salon kontrolu once gelir; ayriligi netlik icin).
        long brans = createBranch(tenant, "Brans-salon");
        long ogr1 = createTeacher(tenant, "Ogr1-salon");
        long ogr2 = createTeacher(tenant, "Ogr2-salon");
        long salon = createRoom(tenant, "Ortak-salon");
        long grup1 = createGroup(tenant, grupJson("G1", brans, ogr1, salon));
        long grup2 = createGroup(tenant, grupJson("G2", brans, ogr2, salon));

        createSchedule(tenant, scheduleJson(grup1, "CARSAMBA", "10:00", "11:30"));

        // Ayni salon, ayni gun, ortusen saat -> 409.
        mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup2, "CARSAMBA", "11:00", "12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void ogretmenCakismasi_ayniOgretmenFarkliSalonOrtusenSaat_409() throws Exception {
        String tenant = "66666666-6666-6666-6666-666666666666";
        // Iki salon, tek ogretmen; iki grup ayni ogretmen, farkli salon.
        long brans = createBranch(tenant, "Brans-ogr");
        long ogr = createTeacher(tenant, "Ortak-ogr");
        long salon1 = createRoom(tenant, "Salon1-ogr");
        long salon2 = createRoom(tenant, "Salon2-ogr");
        long grup1 = createGroup(tenant, grupJson("G1", brans, ogr, salon1));
        long grup2 = createGroup(tenant, grupJson("G2", brans, ogr, salon2));

        createSchedule(tenant, scheduleJson(grup1, "PERSEMBE", "10:00", "11:00"));

        // Farkli salon (salon cakismasi yok) ama ayni ogretmen + ortusme -> 409.
        mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup2, "PERSEMBE", "10:30", "11:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void ortusmeyenSaatler_arkaArkaya_ikiside201() throws Exception {
        String tenant = "77777777-7777-7777-7777-777777777777";
        long grup = createGrupWithRefs(tenant, "backtoback");

        createSchedule(tenant, scheduleJson(grup, "CUMA", "09:00", "10:00"));
        // 10:00-11:00 arka arkaya (ortusme yok cunku 10:00 < 10:00 degil) -> 201.
        createSchedule(tenant, scheduleJson(grup, "CUMA", "10:00", "11:00"));

        mockMvc.perform(get("/api/schedules").param("grupId", String.valueOf(grup)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void ozelGrupSalonsuz_salonCakismasiAtlanir_201() throws Exception {
        String tenant = "88888888-8888-8888-8888-888888888888";
        // Iki OZEL (salonsuz) grup, farkli ogretmen (ogretmen cakismasi tetiklenmesin) -> ikisi de
        // ayni gun ortusen saatte 201 (salon kontrolu atlanir).
        long brans = createBranch(tenant, "Brans-ozel");
        long ogr1 = createTeacher(tenant, "Ozel-ogr1");
        long ogr2 = createTeacher(tenant, "Ozel-ogr2");
        long grup1 = createGroup(tenant, ozelJson("O1", brans, ogr1));
        long grup2 = createGroup(tenant, ozelJson("O2", brans, ogr2));

        createSchedule(tenant, scheduleJson(grup1, "PAZAR", "10:00", "11:00"));
        // Ayni gun ortusen saat ama salonsuz + farkli ogretmen -> 201.
        createSchedule(tenant, scheduleJson(grup2, "PAZAR", "10:30", "11:30"));
    }

    @Test
    void filtreler_grupGunAktif_vePatchActive() throws Exception {
        String tenant = "99999999-9999-9999-9999-999999999999";
        long grup1 = createGrupWithRefs(tenant, "f1");
        long grup2 = createGrupWithRefs(tenant, "f2");

        long s1 = createSchedule(tenant, scheduleJson(grup1, "PAZARTESI", "09:00", "10:00"));
        long s2 = createSchedule(tenant, scheduleJson(grup2, "SALI", "09:00", "10:00"));

        // grupId filtresi.
        mockMvc.perform(get("/api/schedules").param("grupId", String.valueOf(grup1)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(s1));

        // gun filtresi.
        mockMvc.perform(get("/api/schedules").param("gun", "SALI").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(s2));

        // PATCH active -> pasiflestir.
        mockMvc.perform(patch("/api/schedules/{id}/active", s1)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        // aktif=false -> sadece s1.
        mockMvc.perform(get("/api/schedules").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(s1));
        // aktif=true -> sadece s2.
        mockMvc.perform(get("/api/schedules").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(s2));
    }

    @Test
    void grupYardimciUcu_sirali() throws Exception {
        String tenant = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        long grup = createGrupWithRefs(tenant, "helper");

        // Karisik sirayla eklenir; sonuc gun, sonra baslangic saati sirali olmali.
        createSchedule(tenant, scheduleJson(grup, "CARSAMBA", "14:00", "15:00"));
        createSchedule(tenant, scheduleJson(grup, "PAZARTESI", "11:00", "12:00"));
        createSchedule(tenant, scheduleJson(grup, "PAZARTESI", "09:00", "10:00"));

        mockMvc.perform(get("/api/groups/{id}/schedules", grup).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].gun").value("PAZARTESI"))
                .andExpect(jsonPath("$.data[0].baslangicSaati").value("09:00:00"))
                .andExpect(jsonPath("$.data[1].gun").value("PAZARTESI"))
                .andExpect(jsonPath("$.data[1].baslangicSaati").value("11:00:00"))
                .andExpect(jsonPath("$.data[2].gun").value("CARSAMBA"));
    }

    @Test
    void update_cakismaKendiKaydiHaric() throws Exception {
        String tenant = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        long grup = createGrupWithRefs(tenant, "upd");
        long id = createSchedule(tenant, scheduleJson(grup, "SALI", "10:00", "11:00"));

        // Ayni kaydin saatini gunceller (kendiyle cakisma sayilmaz) -> 200.
        mockMvc.perform(put("/api/schedules/{id}", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(grup, "SALI", "10:30", "11:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baslangicSaati").value("10:30:00"));
    }

    @Test
    void yetki_teacherPost403_frontdeskPost403_frontdeskGet200_adminPost201() throws Exception {
        String tenant = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        long grup = createGrupWithRefs(tenant, "yetki");
        String json = scheduleJson(grup, "PAZARTESI", "10:00", "11:00");

        // TEACHER POST -> 403.
        mockMvc.perform(post("/api/schedules")
                        .with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // TEACHER GET -> 403.
        mockMvc.perform(get("/api/schedules").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());

        // FRONTDESK POST -> 403.
        mockMvc.perform(post("/api/schedules")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // FRONTDESK GET -> 200.
        mockMvc.perform(get("/api/schedules").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isOk());

        // ADMIN POST -> 201.
        mockMvc.perform(post("/api/schedules")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
