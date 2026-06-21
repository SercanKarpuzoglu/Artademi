package com.artademi.platform;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * Abonelik uc-ucu davranisi: grace tenant'i is uclarini KULLANABILIR (200), ASKIDA tenant 403
 * TENANT_SUSPENDED; platform listesi abonelik ozeti tasir; PATCH /subscription markPaid telafi eder;
 * provisioning trial aboneligi otomatik yaratir. Yetki: yalniz SUPER_ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SubscriptionEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    /** POST tenant Keycloak'a cikmadan calissin diye provisioner taklit edilir. */
    @MockBean
    TenantAdminProvisioner adminProvisioner;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SubscriptionRepository subRepo;

    @Autowired
    TenantRepository tenantRepo;

    private static RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(b -> b.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private static RequestPostProcessor token(UUID tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private Tenant newTenant(TenantStatus status) {
        Tenant t = Tenant.create("Sub Uc " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private void craft(UUID tenantId, SubscriptionStatus st, LocalDate periodEnd, LocalDate grace,
            PaymentStatus pay) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.now(), 14);
        s.setStatus(st);
        s.setCurrentPeriodEnd(periodEnd);
        s.setGraceEndsAt(grace);
        s.setPaymentStatus(pay);
        subRepo.save(s);
    }

    @Test
    void graceTenant_isUcu200() throws Exception {
        LocalDate now = LocalDate.now();
        Tenant t = newTenant(TenantStatus.AKTIF); // grace'te tenant AKTIF kalir
        craft(t.getId(), SubscriptionStatus.ODEME_BEKLIYOR, now.minusDays(2), now.plusDays(12),
                PaymentStatus.BEKLIYOR);

        mockMvc.perform(get("/api/students").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void askidaTenant_isUcu403() throws Exception {
        Tenant t = newTenant(TenantStatus.ASKIDA);
        craft(t.getId(), SubscriptionStatus.ASKIDA, LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(10), PaymentStatus.BEKLIYOR);

        mockMvc.perform(get("/api/students").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_SUSPENDED"));
    }

    @Test
    void platformListesi_aboneligiTasir() throws Exception {
        // Lina (11111111) V14 seed aboneligi: AKTIF.
        mockMvc.perform(get("/api/platform/tenants").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.ad=='Lina Sanat Merkezi')].subscription.status")
                        .value(hasItem("AKTIF")));
    }

    @Test
    void patchSubscription_markPaid_telafiEder() throws Exception {
        Tenant t = newTenant(TenantStatus.ASKIDA);
        craft(t.getId(), SubscriptionStatus.ASKIDA, LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(10), PaymentStatus.BEKLIYOR);

        mockMvc.perform(patch("/api/platform/tenants/{id}/subscription", t.getId())
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"ODENDI\",\"currentPeriodEnd\":\"2027-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AKTIF"))
                .andExpect(jsonPath("$.data.paymentStatus").value("ODENDI"))
                .andExpect(jsonPath("$.data.currentPeriodEnd").value("2027-01-01"));

        // Telafi: tenant da AKTIF'e dondu.
        org.assertj.core.api.Assertions.assertThat(
                        tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void patchSubscription_normalAdmin_403() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        mockMvc.perform(patch("/api/platform/tenants/{id}/subscription", t.getId())
                        .with(token(t.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"ODENDI\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void provisioning_trialAboneligiOtomatikOlusur() throws Exception {
        given(adminProvisioner.provision(any(UUID.class), any(), any(), any()))
                .willAnswer(inv -> new TenantAdminProvisioner.ProvisionedAdmin("yon", inv.getArgument(1)));

        String body = mockMvc.perform(post("/api/platform/tenants").with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Trial Kurum\",\"adminEmail\":\"a@trial.com\","
                                + "\"adminAd\":\"A\",\"adminSoyad\":\"B\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID tenantId = UUID.fromString(
                objectMapper.readTree(body).path("data").path("tenant").path("id").asText());

        Subscription s = subRepo.findByTenantId(tenantId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(s.getStatus())
                .isEqualTo(SubscriptionStatus.DENEME);
        org.assertj.core.api.Assertions.assertThat(s.getPaymentStatus())
                .isEqualTo(PaymentStatus.BEKLIYOR);
    }
}
