package com.artademi.basvuru;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Public on kayit formu — KIMLIKSIZ uc.
 *
 * <p>Bu sinif projenin en hassas guvenlik istisnasini kilitler: tenant JWT'den DEGIL,
 * URL'deki slug'dan cozuluyor. Testler istisnanin sinirlarini dogrular:
 * yanlis slug ve ASKIDA kurum 404; baska kurumun brans id'si kabul edilmiyor;
 * ve en onemlisi {@link #kimliksizIstek_TenantContextSizdirmaz()} — baglam istekten
 * sonra thread'de KALMIYOR (havuzdaki thread bir sonraki istege tenant tasirsa
 * capraz-tenant sizinti olurdu).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PublicBasvuruTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    BasvuruRepository basvuruRepo;

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private Tenant kurumOlustur(TenantStatus status, String slug) {
        Tenant t = Tenant.create("Kurum " + UUID.randomUUID());
        t.setStatus(status);
        t.setBasvuruSlug(slug);
        return tenantRepo.save(t);
    }

    private static String form(String ad, String telefon) {
        return "{\"ad\":\"" + ad + "\",\"soyad\":\"Yılmaz\",\"telefon\":\"" + telefon + "\"}";
    }

    // ---------- form bilgisi ----------

    @Test
    void formBilgisi_aktifKurum_kurumAdiDoner() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "bale-akademi-" + UUID.randomUUID());

        mockMvc.perform(get("/api/public/basvuru/{slug}", t.getBasvuruSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kurumAdi").value(t.getAd()));
    }

    @Test
    void formBilgisi_olmayanSlug_404() throws Exception {
        mockMvc.perform(get("/api/public/basvuru/{slug}", "boyle-bir-kurum-yok"))
                .andExpect(status().isNotFound());
    }

    @Test
    void formBilgisi_askidakiKurum_404() throws Exception {
        // Odemesi duran kurum altyapimiz uzerinden talep TOPLAYAMAZ.
        Tenant t = kurumOlustur(TenantStatus.ASKIDA, "askida-" + UUID.randomUUID());

        mockMvc.perform(get("/api/public/basvuru/{slug}", t.getBasvuruSlug()))
                .andExpect(status().isNotFound());
    }

    @Test
    void formBilgisi_slugTanimsizKurum_404() throws Exception {
        // Slug NULL = kurum ozelligi acmamis; hicbir form gorunmemeli.
        Tenant t = kurumOlustur(TenantStatus.AKTIF, null);
        assertThat(t.getBasvuruSlug()).isNull();

        mockMvc.perform(get("/api/public/basvuru/{slug}", "null"))
                .andExpect(status().isNotFound());
    }

    // ---------- gonderim ----------

    @Test
    void gonderim_kayitOlusur_veDogruTenantaYazilir() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "gonderim-" + UUID.randomUUID());

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Zeynep", "05551110001")))
                .andExpect(status().isOk());

        // Kayit GERCEKTEN o kurumun altina yazilmis olmali.
        TenantContext.set(t.getId());
        try {
            assertThat(basvuruRepo.findAll())
                    .extracting(Basvuru::getAd)
                    .contains("Zeynep");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void gonderim_honeypotDolu_kayitACILMAZ_amaBasariDoner() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "honeypot-" + UUID.randomUUID());

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Bot\",\"soyad\":\"Bot\",\"telefon\":\"05551110002\","
                                + "\"website\":\"http://spam\"}"))
                // Bota engellendigini SEZDIRMEYIZ.
                .andExpect(status().isOk());

        TenantContext.set(t.getId());
        try {
            assertThat(basvuruRepo.findAll()).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void gonderim_askidakiKurum_404() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.ASKIDA, "askida-post-" + UUID.randomUUID());

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Ada", "05551110003")))
                .andExpect(status().isNotFound());
    }

    @Test
    void gonderim_baskaKurumunBransi_404() throws Exception {
        // Istemci baska kurumun brans id'sini gondererek capraz-tenant referans KURAMAZ.
        Tenant a = kurumOlustur(TenantStatus.AKTIF, "brans-a-" + UUID.randomUUID());
        Tenant b = kurumOlustur(TenantStatus.AKTIF, "brans-b-" + UUID.randomUUID());

        String bransJson = "{\"ad\":\"Bale\"}";
        String cevap = mockMvc.perform(post("/api/branches")
                        .with(token(b.getId().toString(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bransJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bBransId = objectMapper.readTree(cevap).path("data").path("id").asLong();

        mockMvc.perform(post("/api/public/basvuru/{slug}", a.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ada\",\"soyad\":\"Yılmaz\",\"telefon\":\"05551110004\","
                                + "\"bransId\":" + bBransId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void gonderim_zorunluAlanEksik_400() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "validasyon-" + UUID.randomUUID());

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"\",\"soyad\":\"\",\"telefon\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- en kritik: baglam sizintisi ----------

    @Test
    void kimliksizIstek_TenantContextSizdirmaz() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "sizinti-" + UUID.randomUUID());
        assertThat(TenantContext.get()).isNull();

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Sızıntı", "05551110005")))
                .andExpect(status().isOk());

        // Baglam istekten sonra thread'de KALMAMALI: kalirsa havuzdaki thread bir sonraki
        // istege bu tenant'i tasir ve capraz-tenant sizinti olur.
        assertThat(TenantContext.get()).isNull();
    }

    // ---------- soguma (bot korumasi) ----------

    @Test
    void soguma_ayniFormaHizliTekrar_409() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "soguma-" + UUID.randomUUID());
        String ip = "203.0.113.10";

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Ilk", "05551110010")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Ikinci", "05551110011")))
                .andExpect(status().isConflict());
    }

    @Test
    void soguma_ayniIpFarkliForm_ENGELLENMEZ() throws Exception {
        // ⚠️ Gercek senaryo: ortak IP (NAT/ofis/site) arkasindaki iki veli FARKLI kurumlara
        // basvuruyor. Soguma yalnizca IP ile anahtarlansaydi ikincisi kaybolurdu.
        Tenant a = kurumOlustur(TenantStatus.AKTIF, "ortak-ip-a-" + UUID.randomUUID());
        Tenant b = kurumOlustur(TenantStatus.AKTIF, "ortak-ip-b-" + UUID.randomUUID());
        String paylasilanIp = "203.0.113.20";

        mockMvc.perform(post("/api/public/basvuru/{slug}", a.getBasvuruSlug())
                        .header("X-Forwarded-For", paylasilanIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Veli Bir", "05551110012")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/basvuru/{slug}", b.getBasvuruSlug())
                        .header("X-Forwarded-For", paylasilanIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Veli Iki", "05551110013")))
                .andExpect(status().isOk());
    }

    @Test
    void gonderim_ayniTelefonMukerrer_ikinciKayitACILMAZ() throws Exception {
        Tenant t = kurumOlustur(TenantStatus.AKTIF, "mukerrer-" + UUID.randomUUID());
        String telefon = "05551110014";

        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .header("X-Forwarded-For", "203.0.113.30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Ada", telefon)))
                .andExpect(status().isOk());

        // Farkli IP -> soguma devrede degil; engel MUKERRER TELEFON kuralindan gelmeli.
        // Veliye hata gosterilmez (tekrar tekrar denemesin) ama ikinci kayit acilmaz.
        mockMvc.perform(post("/api/public/basvuru/{slug}", t.getBasvuruSlug())
                        .header("X-Forwarded-For", "203.0.113.31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form("Ada Tekrar", telefon)))
                .andExpect(status().isOk());

        TenantContext.set(t.getId());
        try {
            assertThat(basvuruRepo.findAll()).hasSize(1);
        } finally {
            TenantContext.clear();
        }
    }
}
