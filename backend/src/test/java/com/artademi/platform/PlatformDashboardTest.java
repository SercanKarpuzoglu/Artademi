package com.artademi.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.dto.PlatformDashboardResponse;
import java.math.BigDecimal;
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
 * Platform genel bakis: yetki (yalniz SUPER_ADMIN), sayimlar, MRR ve dikkat listesi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformDashboardTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlatformDashboardService service;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    SubscriptionRepository subRepo;

    private static RequestPostProcessor superAdmin() {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        return jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities(authorities);
    }

    private static RequestPostProcessor tenantAdmin() {
        return jwt().jwt(b -> b.claim("tenant_id", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Tenant newTenant(TenantStatus status) {
        Tenant t = Tenant.create("Ops " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private Subscription newSub(UUID tenantId, SubscriptionStatus st, Plan plan,
            PaymentStatus pay, LocalDate periodEnd, String providerRef) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.now(), 14);
        s.setStatus(st);
        s.setPlan(plan);
        s.setPaymentStatus(pay);
        s.setCurrentPeriodEnd(periodEnd);
        s.setProviderSubscriptionRef(providerRef);
        return subRepo.save(s);
    }

    @Test
    void yalnizSuperAdmin_erisebilir() throws Exception {
        mockMvc.perform(get("/api/platform/dashboard").with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kurumlar.toplam").isNumber());

        // Is kullanicisi platform ucuna erisemez (403).
        mockMvc.perform(get("/api/platform/dashboard").with(tenantAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mrr_yalnizOdeyenAktifAylikAbonelikleriSayar() {
        Tenant odeyen = newTenant(TenantStatus.AKTIF);
        newSub(odeyen.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI,
                LocalDate.now().plusMonths(1), "SUB-MRR");

        Tenant deneme = newTenant(TenantStatus.AKTIF);
        newSub(deneme.getId(), SubscriptionStatus.DENEME, Plan.DENEME, PaymentStatus.BEKLIYOR,
                LocalDate.now().plusDays(10), null);

        Tenant silinmis = newTenant(TenantStatus.SILINDI);
        newSub(silinmis.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI,
                LocalDate.now().plusMonths(1), "SUB-SILINMIS");

        PlatformDashboardResponse d = service.build(LocalDate.now());

        // Deneme ve SILINDI kurum gelire SAYILMAZ (gelir abartilmaz).
        assertThat(d.gelir().odeyenKurum()).isGreaterThanOrEqualTo(1);
        assertThat(d.gelir().aylikTekrarlayan()).isEqualByComparingTo(
                d.gelir().aylikPlanUcreti().multiply(BigDecimal.valueOf(d.gelir().odeyenKurum())));

        List<UUID> gelirdekiler = d.yaklasanYenilemeler().stream()
                .map(PlatformDashboardResponse.YenilemeSatiri::tenantId).toList();
        assertThat(gelirdekiler).doesNotContain(silinmis.getId());
    }

    @Test
    void graceVeAskidakiler_dikkatListesindeSebepleGelir() {
        Tenant grace = newTenant(TenantStatus.AKTIF); // grace'te tenant AKTIF kalir
        newSub(grace.getId(), SubscriptionStatus.ODEME_BEKLIYOR, Plan.AYLIK, PaymentStatus.BEKLIYOR,
                LocalDate.now().minusDays(2), "SUB-GRACE");

        PlatformDashboardResponse d = service.build(LocalDate.now());

        assertThat(d.dikkatGerektirenler())
                .anySatisfy(satir -> {
                    assertThat(satir.tenantId()).isEqualTo(grace.getId());
                    assertThat(satir.sebep()).contains("Ödeme bekleniyor");
                });
    }

    @Test
    void yaklasanYenilemeler_yalniz7GunIcindekiler() {
        Tenant yakin = newTenant(TenantStatus.AKTIF);
        newSub(yakin.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI,
                LocalDate.now().plusDays(3), "SUB-YAKIN");

        Tenant uzak = newTenant(TenantStatus.AKTIF);
        newSub(uzak.getId(), SubscriptionStatus.AKTIF, Plan.AYLIK, PaymentStatus.ODENDI,
                LocalDate.now().plusDays(30), "SUB-UZAK");

        PlatformDashboardResponse d = service.build(LocalDate.now());
        List<UUID> idler = d.yaklasanYenilemeler().stream()
                .map(PlatformDashboardResponse.YenilemeSatiri::tenantId).toList();

        assertThat(idler).contains(yakin.getId()).doesNotContain(uzak.getId());
    }

    @Test
    void silinmisKurum_toplamdaSayilmaz() {
        PlatformDashboardResponse once = service.build(LocalDate.now());
        long oncekiToplam = once.kurumlar().toplam();

        newTenant(TenantStatus.SILINDI);

        PlatformDashboardResponse sonra = service.build(LocalDate.now());
        assertThat(sonra.kurumlar().toplam()).isEqualTo(oncekiToplam);
        assertThat(sonra.kurumlar().statuBazinda().get(TenantStatus.SILINDI))
                .isGreaterThanOrEqualTo(1L);
    }
}
