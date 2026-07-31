package com.artademi.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.PlatformBillingService.Filtre;
import com.artademi.platform.dto.PlatformSubscriptionRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Odeme takibi: yetki, is-dili filtreleri (ODEYEN/DENEME/GECIKMIS/ASKIDA) ve hareket sayfalama.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformBillingTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlatformBillingService service;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    SubscriptionRepository subRepo;

    private static RequestPostProcessor superAdmin() {
        return jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private static RequestPostProcessor tenantAdmin() {
        return jwt().jwt(b -> b.claim("tenant_id", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Tenant tenant(String ad, TenantStatus status) {
        Tenant t = Tenant.create(ad + " " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private void sub(UUID tenantId, SubscriptionStatus st, Plan plan, PaymentStatus pay,
            String providerRef) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.now(), 14);
        s.setStatus(st);
        s.setPlan(plan);
        s.setPaymentStatus(pay);
        s.setProviderSubscriptionRef(providerRef);
        subRepo.save(s);
    }

    private List<UUID> idlerinde(Filtre filtre) {
        return service.subscriptionRows(filtre, null).stream()
                .map(PlatformSubscriptionRow::tenantId).toList();
    }

    @Test
    void yalnizSuperAdmin_erisebilir() throws Exception {
        mockMvc.perform(get("/api/platform/billing/subscriptions").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/platform/billing/subscriptions").with(tenantAdmin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/platform/billing/events").with(tenantAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtreler_dogruKurumlariAyirir() {
        Tenant odeyen = tenant("Odeyen", TenantStatus.AKTIF);
        sub(odeyen.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI, "SUB-O");

        Tenant deneme = tenant("Deneme", TenantStatus.AKTIF);
        sub(deneme.getId(), SubscriptionStatus.DENEME, Plan.DENEME, PaymentStatus.BEKLIYOR, null);

        Tenant gecikmis = tenant("Gecikmis", TenantStatus.AKTIF); // grace: tenant AKTIF kalir
        sub(gecikmis.getId(), SubscriptionStatus.ODEME_BEKLIYOR, Plan.AYLIK, PaymentStatus.BEKLIYOR,
                "SUB-G");

        Tenant askida = tenant("Askida", TenantStatus.ASKIDA);
        sub(askida.getId(), SubscriptionStatus.ASKIDA, Plan.AYLIK, PaymentStatus.BEKLIYOR, "SUB-A");

        assertThat(idlerinde(Filtre.ODEYEN)).contains(odeyen.getId())
                .doesNotContain(deneme.getId(), gecikmis.getId(), askida.getId());
        assertThat(idlerinde(Filtre.DENEME)).contains(deneme.getId())
                .doesNotContain(odeyen.getId());
        assertThat(idlerinde(Filtre.GECIKMIS)).contains(gecikmis.getId())
                .doesNotContain(odeyen.getId(), deneme.getId());
        assertThat(idlerinde(Filtre.ASKIDA)).contains(askida.getId())
                .doesNotContain(odeyen.getId());
    }

    @Test
    void silinmisKurum_yalnizcaHEPSIdeGorunur() {
        Tenant silinmis = tenant("Silinmis", TenantStatus.SILINDI);
        sub(silinmis.getId(), SubscriptionStatus.IPTAL, Plan.AYLIK, PaymentStatus.BEKLIYOR, null);

        assertThat(idlerinde(Filtre.HEPSI)).contains(silinmis.getId());
        assertThat(idlerinde(Filtre.ASKIDA)).doesNotContain(silinmis.getId());
        assertThat(idlerinde(Filtre.ODEYEN)).doesNotContain(silinmis.getId());
    }

    @Test
    void aramaKurumAdindaCalisir() {
        Tenant t = tenant("BulunacakKurum", TenantStatus.AKTIF);
        sub(t.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI, "SUB-B");

        // Buyuk/kucuk harf duyarsiz, parcali eslesme.
        assertThat(service.subscriptionRows(Filtre.HEPSI, "bulunacakkurum"))
                .anyMatch(r -> r.tenantId().equals(t.getId()));
        assertThat(service.subscriptionRows(Filtre.HEPSI, "boyle-bir-kurum-yok")).isEmpty();
    }

    @Test
    void otomatikOdemeBayragi_saglayiciBagindanGelir() {
        Tenant otomatik = tenant("Otomatik", TenantStatus.AKTIF);
        sub(otomatik.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI, "SUB-OTO");
        Tenant manuel = tenant("Manuel", TenantStatus.AKTIF);
        sub(manuel.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI, null);

        List<PlatformSubscriptionRow> rows = service.subscriptionRows(Filtre.ODEYEN, null);
        assertThat(rows).anySatisfy(r -> {
            if (r.tenantId().equals(otomatik.getId())) {
                assertThat(r.otomatikOdeme()).isTrue();
            }
        });
        assertThat(rows).anySatisfy(r -> {
            if (r.tenantId().equals(manuel.getId())) {
                assertThat(r.otomatikOdeme()).isFalse();
            }
        });
    }

    @Test
    void hareketler_sayfaliVeMetaIleDoner() throws Exception {
        mockMvc.perform(get("/api/platform/billing/events?page=0&size=5").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.totalElements").isNumber());
    }
}
