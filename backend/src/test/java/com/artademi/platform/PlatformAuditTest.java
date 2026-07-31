package com.artademi.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.audit.AuditAction;
import com.artademi.platform.audit.PlatformAudit;
import com.artademi.platform.audit.PlatformAuditRepository;
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
 * Denetim izi: sorumluluk doguran islemler iz birakir, iz DEGISTIRILEMEZ, yalniz SUPER_ADMIN okur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformAuditTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    /** Keycloak'a ag cikisi olmasin: tenant provisioning/kullanici islemleri mock'lanir. */
    @MockBean
    TenantAdminProvisioner provisioner;

    @MockBean
    TenantUserAdmin tenantUserAdmin;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlatformAuditRepository auditRepo;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    SubscriptionRepository subRepo;

    private static RequestPostProcessor superAdmin() {
        return jwt().jwt(b -> b.claim("preferred_username", "super.admin")
                        .claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private static RequestPostProcessor tenantAdmin() {
        return jwt().jwt(b -> b.claim("tenant_id", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Tenant tenant() {
        return tenantRepo.save(Tenant.create("Denetim " + UUID.randomUUID()));
    }

    private List<PlatformAudit> izler(UUID tenantId) {
        return auditRepo.findByTargetTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Test
    void kurumOlusturma_izBirakir_actorTokendanGelir() throws Exception {
        given(provisioner.provision(any(), anyString(), anyString(), anyString()))
                .willReturn(new TenantAdminProvisioner.ProvisionedAdmin("yonetici", "a@b.com"));

        mockMvc.perform(post("/api/platform/tenants")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ad":"Denetim Testi %s","adminEmail":"a@b.com",
                                 "adminAd":"Ada","adminSoyad":"Yılmaz"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        assertThat(auditRepo.findAll())
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo(AuditAction.KURUM_OLUSTURULDU);
                    assertThat(a.getActor()).isEqualTo("super.admin");
                });
    }

    @Test
    void durumDegisikligi_eskiVeYeniDurumuYazar() throws Exception {
        Tenant t = tenant();

        mockMvc.perform(patch("/api/platform/tenants/{id}/status", t.getId())
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASKIDA\"}"))
                .andExpect(status().isOk());

        assertThat(izler(t.getId()))
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo(AuditAction.KURUM_DURUMU_DEGISTI);
                    assertThat(a.getDetail()).contains("AKTIF").contains("ASKIDA");
                    assertThat(a.getTargetAd()).isEqualTo(t.getAd());
                });
    }

    @Test
    void ayniDuruma_tekrarPatch_izYAZMAZ() throws Exception {
        Tenant t = tenant(); // AKTIF olusur

        mockMvc.perform(patch("/api/platform/tenants/{id}/status", t.getId())
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AKTIF\"}"))
                .andExpect(status().isOk());

        // Degisiklik olmadi → gurultu yok.
        assertThat(izler(t.getId()))
                .noneMatch(a -> a.getAction() == AuditAction.KURUM_DURUMU_DEGISTI);
    }

    @Test
    void silme_iziBirakir_kurumAdiSnapshotOlarakKalir() throws Exception {
        Tenant t = tenant();
        String adOnce = t.getAd();

        mockMvc.perform(delete("/api/platform/tenants/{id}", t.getId()).with(superAdmin()))
                .andExpect(status().isOk());

        assertThat(izler(t.getId()))
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo(AuditAction.KURUM_SILINDI);
                    // Ad snapshot: kurum silinse de iz okunabilir kalir.
                    assertThat(a.getTargetAd()).isEqualTo(adOnce);
                });
    }

    @Test
    void abonelikGuncelleme_izBirakir() throws Exception {
        Tenant t = tenant();
        subRepo.save(Subscription.createTrial(t.getId(), java.time.LocalDate.now(), 14));

        mockMvc.perform(patch("/api/platform/tenants/{id}/subscription", t.getId())
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"ODENDI\",\"currentPeriodEnd\":\"2027-01-01\"}"))
                .andExpect(status().isOk());

        assertThat(izler(t.getId()))
                .anySatisfy(a -> {
                    assertThat(a.getAction()).isEqualTo(AuditAction.ABONELIK_GUNCELLENDI);
                    assertThat(a.getDetail()).contains("ODENDI").contains("2027-01-01");
                });
    }

    @Test
    void listeUcu_yalnizSuperAdmin_veSayfali() throws Exception {
        mockMvc.perform(get("/api/platform/audit?page=0&size=5").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.totalElements").isNumber());

        mockMvc.perform(get("/api/platform/audit").with(tenantAdmin()))
                .andExpect(status().isForbidden());
    }
}
