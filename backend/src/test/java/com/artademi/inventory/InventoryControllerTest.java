package com.artademi.inventory;

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
 * Stok/Urun Satisi (inventory) entegrasyon testleri — gercek PostgreSQL (Testcontainers) + MockMvc +
 * JWT post-processor. Tenant izolasyonu (PK-find + capraz-tenant urun/ogrenci sizinti yok), stok dusumu,
 * yetersiz stok (409, stok degismez), birimFiyat satis aninda kopyalama (fiyat degisse de sabit),
 * validasyon (adet/fiyat), stok mutlak atama, Specification filtreleri ve yetki dogrular.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryControllerTest {

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

    // === Olusturma yardimcilari ===

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

    private String productJson(String ad, String satisFiyati, int stok) {
        return "{\"ad\":\"" + ad + "\",\"satisFiyati\":" + satisFiyati + ",\"stokAdedi\":" + stok + "}";
    }

    private long createProduct(String tenantId, String ad, String satisFiyati, int stok) throws Exception {
        String body = mockMvc.perform(post("/api/products")
                        .with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(ad, satisFiyati, stok)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.aktif").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String saleJson(long urunId, Long ogrenciId, int adet) {
        String ogr = ogrenciId == null ? "" : ",\"ogrenciId\":" + ogrenciId;
        return "{\"urunId\":" + urunId + ogr + ",\"adet\":" + adet + "}";
    }

    private int getStok(String tenantId, long urunId) throws Exception {
        String body = mockMvc.perform(get("/api/products/{id}", urunId).with(admin(tenantId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("stokAdedi").asInt();
    }

    // === Tenant izolasyonu / PK-find ===

    @Test
    void tenantIzolasyonu_urunBaskaTenantGoremezVe404() throws Exception {
        long urunA = createProduct(TENANT_A, "UrunIzol", "100.00", 5);

        // B baglaminda liste -> A'nin urunu gorunmez.
        mockMvc.perform(get("/api/products").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin id'siyle GET -> 404 (PK-find sizinti OLMAMALI).
        mockMvc.perform(get("/api/products/{id}", urunA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void tenantIzolasyonu_satisBaskaTenantGoremezVe404() throws Exception {
        long urunA = createProduct(TENANT_A, "SatisIzol", "50.00", 10);
        String body = mockMvc.perform(post("/api/sales")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urunA, null, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long satisA = objectMapper.readTree(body).path("data").path("id").asLong();

        // B baglaminda liste -> A'nin satisi gorunmez.
        mockMvc.perform(get("/api/sales").with(admin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B, A'nin satis id'siyle GET -> 404.
        mockMvc.perform(get("/api/sales/{id}", satisA).with(admin(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantUrun404() throws Exception {
        long urunA = createProduct(TENANT_A, "CrossUrun", "10.00", 5);

        // B baglaminda A'nin urunuyle satis -> 404.
        mockMvc.perform(post("/api/sales")
                        .with(admin(TENANT_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urunA, null, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void caprazTenantOgrenci404() throws Exception {
        String tenant = "33333333-3333-3333-3333-333333333333";
        long urun = createProduct(tenant, "OgrCross", "10.00", 5);
        long ogrenciB = createStudent(TENANT_B, "OgrB", "31000000001");

        // tenant baglaminda B'nin ogrencisiyle satis -> 404.
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urun, ogrenciB, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // === Stok dusumu + tutar hesabi ===

    @Test
    void mutluYol_satis_stokDuser_toplamTutarTam() throws Exception {
        String tenant = "44444444-4444-4444-4444-444444444444";
        long urun = createProduct(tenant, "Defter", "12.50", 10);

        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urun, null, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adet").value(3))
                .andExpect(jsonPath("$.data.birimFiyat").value(12.50))
                .andExpect(jsonPath("$.data.toplamTutar").value(37.50))
                .andExpect(jsonPath("$.data.urun.id").value(urun))
                .andExpect(jsonPath("$.data.satisTarihi").exists());

        // Stok 10 -> 7.
        org.junit.jupiter.api.Assertions.assertEquals(7, getStok(tenant, urun));
    }

    @Test
    void yetersizStok_409_stokDegismez() throws Exception {
        String tenant = "55555555-5555-5555-5555-555555555555";
        long urun = createProduct(tenant, "Kalem", "5.00", 2);

        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urun, null, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        // Stok hala 2 (degismedi, satir olusmadi).
        org.junit.jupiter.api.Assertions.assertEquals(2, getStok(tenant, urun));
        mockMvc.perform(get("/api/sales").param("urunId", String.valueOf(urun)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void birimFiyat_satisAnindaKopyalanir_urunFiyatiDegisseDeSabit() throws Exception {
        String tenant = "66666666-6666-6666-6666-666666666666";
        long urun = createProduct(tenant, "Silgi", "8.00", 10);

        String body = mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urun, null, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.birimFiyat").value(8.00))
                .andReturn().getResponse().getContentAsString();
        long satis = objectMapper.readTree(body).path("data").path("id").asLong();

        // Urun fiyatini guncelle (PUT).
        mockMvc.perform(put("/api/products/{id}", urun)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Silgi\",\"satisFiyati\":99.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.satisFiyati").value(99.00));

        // Eski satisin birimFiyati DEGISMEZ (hala 8.00).
        mockMvc.perform(get("/api/sales/{id}", satis).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birimFiyat").value(8.00))
                .andExpect(jsonPath("$.data.toplamTutar").value(8.00));
    }

    // === Validasyon ===

    @Test
    void adetSifirVeyaNegatif_400() throws Exception {
        String tenant = "77777777-7777-7777-7777-777777777777";
        long urun = createProduct(tenant, "ValAdet", "10.00", 10);

        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(urun, null, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.adet").exists());
    }

    @Test
    void satisFiyatiSifirVeyaNegatif_400() throws Exception {
        String tenant = "88888888-8888-8888-8888-888888888888";

        mockMvc.perform(post("/api/products")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("ValFiyat", "0.00", 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.satisFiyati").exists());
    }

    @Test
    void eksikZorunluAlan_400() throws Exception {
        String tenant = "99999999-9999-9999-9999-999999999999";

        // ad eksik.
        mockMvc.perform(post("/api/products")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"satisFiyati\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.ad").exists());

        // satis: urunId eksik.
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adet\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.urunId").exists());
    }

    // === Stok mutlak atama ===

    @Test
    void patchStok_mutlakAtama() throws Exception {
        String tenant = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        long urun = createProduct(tenant, "StokSet", "10.00", 3);

        mockMvc.perform(patch("/api/products/{id}/stok", urun)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stokAdedi\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stokAdedi").value(42));

        org.junit.jupiter.api.Assertions.assertEquals(42, getStok(tenant, urun));

        // Negatif -> 400.
        mockMvc.perform(patch("/api/products/{id}/stok", urun)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stokAdedi\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.stokAdedi").exists());
    }

    // === Filtreler ===

    @Test
    void urunFiltreleri_qVeAktif() throws Exception {
        String tenant = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        long defter = createProduct(tenant, "Defter Mavi", "10.00", 5);
        long kalem = createProduct(tenant, "Kalem Kirmizi", "5.00", 5);

        // q = "defter" -> sadece defter.
        mockMvc.perform(get("/api/products").param("q", "defter").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(defter));

        // kalem pasiflestir.
        mockMvc.perform(patch("/api/products/{id}/active", kalem)
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aktif").value(false));

        // aktif=false -> sadece kalem.
        mockMvc.perform(get("/api/products").param("aktif", "false").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(kalem));
        // aktif=true -> sadece defter.
        mockMvc.perform(get("/api/products").param("aktif", "true").with(admin(tenant)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(defter));
    }

    @Test
    void satisFiltreleri_urunOgrenciVeTarih() throws Exception {
        String tenant = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        long urun1 = createProduct(tenant, "F-Urun1", "10.00", 50);
        long urun2 = createProduct(tenant, "F-Urun2", "20.00", 50);
        long ogrenci = createStudent(tenant, "FOgr", "12000000099");

        // urun1 + ogrenci, belirli tarih.
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urunId\":" + urun1 + ",\"ogrenciId\":" + ogrenci
                                + ",\"adet\":1,\"satisTarihi\":\"2026-01-10\"}"))
                .andExpect(status().isCreated());
        // urun2, ogrencisiz, baska tarih.
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urunId\":" + urun2 + ",\"adet\":1,\"satisTarihi\":\"2026-03-10\"}"))
                .andExpect(status().isCreated());

        // urunId filtresi.
        mockMvc.perform(get("/api/sales").param("urunId", String.valueOf(urun1)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].urun.id").value(urun1));

        // ogrenciId filtresi.
        mockMvc.perform(get("/api/sales").param("ogrenciId", String.valueOf(ogrenci)).with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].ogrenci.id").value(ogrenci));

        // Tarih araligi: sadece ocak.
        mockMvc.perform(get("/api/sales")
                        .param("from", "2026-01-01").param("to", "2026-01-31").with(admin(tenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].urun.id").value(urun1));
    }

    // === Yetki ===

    @Test
    void yetki_urun() throws Exception {
        String tenant = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        long urun = createProduct(tenant, "YetkiUrun", "10.00", 5);

        // FRONTDESK POST urun -> 403.
        mockMvc.perform(post("/api/products")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("X", "10.00", 1)))
                .andExpect(status().isForbidden());

        // FRONTDESK GET urun -> 403 (urun okuma ADMIN/FRONTDESK_ACCOUNTING).
        mockMvc.perform(get("/api/products").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());

        // TEACHER GET urun -> 403.
        mockMvc.perform(get("/api/products/{id}", urun).with(token(tenant, "TEACHER")))
                .andExpect(status().isForbidden());

        // FRONTDESK_ACCOUNTING GET urun -> 200.
        mockMvc.perform(get("/api/products/{id}", urun).with(token(tenant, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());

        // FRONTDESK_ACCOUNTING POST urun -> 403 (yazma yalnizca ADMIN).
        mockMvc.perform(post("/api/products")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Y", "10.00", 1)))
                .andExpect(status().isForbidden());

        // ADMIN POST urun -> 201.
        mockMvc.perform(post("/api/products")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Z", "10.00", 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void yetki_satis() throws Exception {
        String tenant = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        long urun = createProduct(tenant, "YetkiSatis", "10.00", 20);
        String json = saleJson(urun, null, 1);

        // FRONTDESK POST satis -> 403.
        mockMvc.perform(post("/api/sales")
                        .with(token(tenant, "FRONTDESK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // TEACHER POST satis -> 403.
        mockMvc.perform(post("/api/sales")
                        .with(token(tenant, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        // FRONTDESK GET satis -> 403.
        mockMvc.perform(get("/api/sales").with(token(tenant, "FRONTDESK")))
                .andExpect(status().isForbidden());

        // FRONTDESK_ACCOUNTING POST satis -> 201.
        mockMvc.perform(post("/api/sales")
                        .with(token(tenant, "FRONTDESK_ACCOUNTING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // ADMIN POST satis -> 201.
        mockMvc.perform(post("/api/sales")
                        .with(admin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
