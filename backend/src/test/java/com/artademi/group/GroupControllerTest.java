package com.artademi.group;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
 * Grup (Group) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc + JWT
 * post-processor. Tenant izolasyonu (PK-find + capraz-tenant brans/ogretmen/salon sizinti yok),
 * @GrupTutarli kosullu validasyon (GRUP salon+aidat / OZEL dersBasiUcret), ozet referans response,
 * Specification filtreleri, aktif filtresi/PATCH active ve yetki dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GroupControllerTest {

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

    /** Bir tenant'ta brans+ogretmen+salon olusturup id'lerini dondurur. */
    private long[] seedRefs(String tenantId, String suffix) throws Exception {
        long brans = createBranch(tenantId, "Brans-" + suffix);
        long ogretmen = createTeacher(tenantId, "Ogretmen-" + suffix);
        long salon = createRoom(tenantId, "Salon-" + suffix);
        return new long[] {brans, ogretmen, salon};
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aktif").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** keycloakUserId (sub) ile ogretmen olusturur — TEACHER /mine koprusu icin. */
    private long createTeacherWithSub(String tenantId, String ad, String sub) throws Exception {
        String json = "{\"ad\":\"" + ad + "\",\"soyad\":\"Hoca\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[],"
                + "\"keycloakUserId\":\"" + sub + "\"}";
        String body = mockMvc.perform(post("/api/teachers")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** TEACHER token: JWT {@code sub} (keycloakUserId eslesmesi icin) + tenant_id + TEACHER rolu. */
    private static RequestPostProcessor teacherToken(String tenantId, String sub) {
        return jwt()
                .jwt(builder -> builder
                        .subject(sub)
                        .claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of("TEACHER"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_TEACHER"));
    }

    @Test
    void mine_teacherKendiGruplari_baskaSizmaz_yeniGrupGorunur() throws Exception {
        String tenant = "a1111111-1111-1111-1111-111111111111";
        long[] refs = seedRefs(tenant, "mine"); // brans + (kullanilmayan ogretmen) + salon
        String sub = "sub-mine-selin";
        long selin = createTeacherWithSub(tenant, "Selin", sub);

        // Selin'in 2 grubu (biri OZEL — HIC yoklamasi yok = tavuk-yumurta senaryosu).
        long g1 = createGroup(tenant, grupJson("Bale Baslangic", refs[0], selin, refs[2]));
        long g2 = createGroup(tenant, ozelJson("Selin ile Ozel", refs[0], selin));

        // Baska ogretmenin grubu — /mine'da ASLA gorunmemeli.
        long diger = createTeacher(tenant, "Diger");
        createGroup(tenant, grupJson("Diger Grup", refs[0], diger, refs[2]));

        mockMvc.perform(get("/api/groups/mine").with(teacherToken(tenant, sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", containsInAnyOrder((int) g1, (int) g2)))
                .andExpect(jsonPath("$.data[*].ogretmen.id", everyItem(is((int) selin))))
                // Tam detay: salon/brans/ucret alanlari dolu.
                .andExpect(jsonPath("$.data[?(@.tip=='GRUP')].brans.id", containsInAnyOrder((int) refs[0])));
    }

    @Test
    void mine_pasifGrupDaGelir() throws Exception {
        String tenant = "a2222222-2222-2222-2222-222222222222";
        long[] refs = seedRefs(tenant, "minepasif");
        String sub = "sub-mine-pasif";
        long t = createTeacherWithSub(tenant, "Pasifci", sub);
        long g = createGroup(tenant, grupJson("Pasif Olacak", refs[0], t, refs[2]));

        mockMvc.perform(patch("/api/groups/{id}/active", g)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk());

        // Hepsi (aktif+pasif) doner -> pasif grup da listede.
        mockMvc.perform(get("/api/groups/mine").with(teacherToken(tenant, sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value((int) g))
                .andExpect(jsonPath("$.data[0].aktif").value(false));
    }

    @Test
    void mine_subEslesmezse_bosListe() throws Exception {
        String tenant = "a3333333-3333-3333-3333-333333333333";
        mockMvc.perform(get("/api/groups/mine").with(teacherToken(tenant, "sub-hic-yok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void mine_yalnizTeacher_admin403_frontdesk403() throws Exception {
        String tenant = "a4444444-4444-4444-4444-444444444444";
        mockMvc.perform(get("/api/groups/mine").with(admin(tenant)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/groups/mine").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mine_genelListeHalaTeacherKapali() throws Exception {
        // /mine acildi ama genel /api/groups TEACHER'a HALA 403 (mevcut davranis korundu).
        String tenant = "a5555555-5555-5555-5555-555555555555";
        mockMvc.perform(get("/api/groups").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mine_crossTenant_sizmaz() throws Exception {
        // Ayni sub iki tenant'ta; /mine yalnizca CARI tenant'in ogretmen grubunu doner.
        String tenantA = "a6666666-6666-6666-6666-666666666666";
        String tenantB = "a7777777-7777-7777-7777-777777777777";
        String sub = "sub-cross";
        long[] refsA = seedRefs(tenantA, "crossA");
        long[] refsB = seedRefs(tenantB, "crossB");
        long tA = createTeacherWithSub(tenantA, "CrossA", sub);
        long tB = createTeacherWithSub(tenantB, "CrossB", sub);
        createGroup(tenantA, grupJson("A Grup", refsA[0], tA, refsA[2]));
        long gB = createGroup(tenantB, grupJson("B Grup", refsB[0], tB, refsB[2]));

        // B baglaminda /mine -> sadece B'nin grubu (A sizmaz).
        mockMvc.perform(get("/api/groups/mine").with(teacherToken(tenantB, sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value((int) gB));
    }

    @Test
    void tenantIzolasyonu_baskaTenantGoremezVe404() throws Exception {
        long[] refs = seedRefs(TENANT_A, "izol");
        long idA = createGroup(TENANT_A, grupJson("A Grubu", refs[0], refs[1], refs[2]));

        // B baglaminda liste -> A'nin kaydi gorunmez.
        mockMvc.perform(get("/api/groups").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/groups/{id}", idA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantReferans_brans404() throws Exception {
        // A'da brans, B'de gecerli ogretmen+salon. A'nin brans id'siyle B'de grup -> 404.
        long bransA = createBranch(TENANT_A, "BransA-only");
        long[] refsB = seedRefs(TENANT_B, "brans-case");

        mockMvc.perform(post("/api/groups")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grupJson("Sizinti", bransA, refsB[1], refsB[2])))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantReferans_ogretmen404() throws Exception {
        long ogretmenA = createTeacher(TENANT_A, "OgrA-only");
        long[] refsB = seedRefs(TENANT_B, "ogr-case");

        mockMvc.perform(post("/api/groups")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grupJson("Sizinti", refsB[0], ogretmenA, refsB[2])))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantReferans_salon404() throws Exception {
        long salonA = createRoom(TENANT_A, "SalonA-only");
        long[] refsB = seedRefs(TENANT_B, "salon-case");

        mockMvc.perform(post("/api/groups")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grupJson("Sizinti", refsB[0], refsB[1], salonA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void tipGrup_salonVeAidatYok400_gecerliGrup201() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long[] refs = seedRefs(tenant, "grup");

        // salon + aylikAidat olmadan -> 400 (alan bazli).
        String eksik = "{\"ad\":\"Eksik\",\"tip\":\"GRUP\",\"bransId\":" + refs[0]
                + ",\"ogretmenId\":" + refs[1] + "}";
        mockMvc.perform(post("/api/groups")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eksik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.salonId").exists())
                .andExpect(jsonPath("$.error.fields.aylikAidat").exists());

        // Gecerli GRUP -> 201, ozet referanslar dolu.
        String body = mockMvc.perform(post("/api/groups")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grupJson("Gecerli", refs[0], refs[1], refs[2])))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tip").value("GRUP"))
                .andExpect(jsonPath("$.data.brans.id").value(refs[0]))
                .andExpect(jsonPath("$.data.ogretmen.id").value(refs[1]))
                .andExpect(jsonPath("$.data.salon.id").value(refs[2]))
                .andReturn().getResponse().getContentAsString();
        // tenant_id sizdirilmaz.
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("tenant"));
    }

    @Test
    void tipOzel_dersBasiUcretYok400_gecerliOzel201() throws Exception {
        String tenant = "55555555-5555-5555-5555-555555555555";
        long[] refs = seedRefs(tenant, "ozel");

        // dersBasiUcret olmadan -> 400.
        String eksik = "{\"ad\":\"Eksik\",\"tip\":\"OZEL\",\"bransId\":" + refs[0]
                + ",\"ogretmenId\":" + refs[1] + "}";
        mockMvc.perform(post("/api/groups")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eksik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.dersBasiUcret").exists());

        // Gecerli OZEL (salon YOK, dersBasiUcret>0) -> 201, salon null.
        mockMvc.perform(post("/api/groups")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ozelJson("Gecerli Ozel", refs[0], refs[1])))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tip").value("OZEL"))
                .andExpect(jsonPath("$.data.salon").doesNotExist())
                .andExpect(jsonPath("$.data.dersBasiUcret").value(300.00));
    }

    @Test
    void filtreler_tipBransOgretmenAktif() throws Exception {
        String tenant = "66666666-6666-6666-6666-666666666666";
        long[] refs1 = seedRefs(tenant, "f1");
        long[] refs2 = seedRefs(tenant, "f2");

        long grupId = createGroup(tenant, grupJson("Grup1", refs1[0], refs1[1], refs1[2]));
        long ozelId = createGroup(tenant, ozelJson("Ozel1", refs2[0], refs2[1]));

        // tip filtresi.
        mockMvc.perform(get("/api/groups").param("tip", "GRUP").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(grupId));

        // bransId filtresi.
        mockMvc.perform(get("/api/groups").param("bransId", String.valueOf(refs2[0])).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ozelId));

        // ogretmenId filtresi.
        mockMvc.perform(get("/api/groups").param("ogretmenId", String.valueOf(refs1[1])).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(grupId));

        // aktif filtresi (ikisi de aktif).
        mockMvc.perform(get("/api/groups").param("aktif", "true").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void patchActive_pasifFiltredeGorunurAktifFiltredeGorunmez() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long[] refs = seedRefs(tenant, "patch");
        long id = createGroup(tenant, grupJson("Patch", refs[0], refs[1], refs[2]));

        mockMvc.perform(patch("/api/groups/{id}/active", id)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        mockMvc.perform(get("/api/groups").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/groups").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void yetki_teacherPost403_frontdeskPost403_frontdeskGet200_adminPost201() throws Exception {
        String tenant = "77777777-7777-7777-7777-777777777777";
        long[] refs = seedRefs(tenant, "yetki");
        String json = grupJson("Yetki", refs[0], refs[1], refs[2]);

        // TEACHER POST -> 403.
        mockMvc.perform(post("/api/groups")
                        .with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // TEACHER GET -> 403.
        mockMvc.perform(get("/api/groups").with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());

        // FRONTDESK POST -> 403.
        mockMvc.perform(post("/api/groups")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // FRONTDESK GET -> 200.
        mockMvc.perform(get("/api/groups").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isOk());

        // ADMIN POST -> 201.
        mockMvc.perform(post("/api/groups")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
