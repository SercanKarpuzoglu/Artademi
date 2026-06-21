package com.artademi.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.artademi.platform.dto.SubscriptionResponse;
import com.artademi.platform.dto.SubscriptionWarning;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abonelik durum-gecis mantigi (SubscriptionService). {@code evaluate()} DOGRUDAN cagrilir
 * (scheduler'a guvenilmez) → deterministik. Grace = uyari (tenant AKTIF kalir), ASKIDA = kesinti
 * (tenant ASKIDA). Lina seed'i (uzak donem) bozulmamali.
 */
@SpringBootTest
@Testcontainers
class SubscriptionServiceTest {

    private static final UUID LINA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    SubscriptionService service;

    @Autowired
    SubscriptionRepository subRepo;

    @Autowired
    TenantRepository tenantRepo;

    private Tenant newTenant(TenantStatus status) {
        Tenant t = Tenant.create("Sub Test " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private Subscription craft(UUID tenantId, SubscriptionStatus status, LocalDate periodEnd,
            LocalDate graceEndsAt, PaymentStatus payment) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.now(), 14);
        s.setStatus(status);
        s.setCurrentPeriodEnd(periodEnd);
        s.setGraceEndsAt(graceEndsAt);
        s.setPaymentStatus(payment);
        return subRepo.save(s);
    }

    @Test
    void createTrial_DENEME_14g_BEKLIYOR() {
        Tenant t = newTenant(TenantStatus.AKTIF);
        SubscriptionResponse r = service.createTrial(t.getId());

        assertThat(r.status()).isEqualTo(SubscriptionStatus.DENEME);
        assertThat(r.plan()).isEqualTo(Plan.DENEME);
        assertThat(r.paymentStatus()).isEqualTo(PaymentStatus.BEKLIYOR);
        assertThat(r.currentPeriodEnd()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(r.graceEndsAt()).isNull();
    }

    @Test
    void evaluate_graceEntry_tenantAktifKalir() {
        LocalDate now = LocalDate.now();
        Tenant t = newTenant(TenantStatus.AKTIF);
        craft(t.getId(), SubscriptionStatus.AKTIF, now.minusDays(1), null, PaymentStatus.BEKLIYOR);

        service.evaluate(now);

        Subscription s = subRepo.findByTenantId(t.getId()).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ODEME_BEKLIYOR);
        assertThat(s.getGraceEndsAt()).isEqualTo(now.minusDays(1).plusDays(14));
        // ⚠️ Grace = uyari: tenant hala AKTIF (erisim surer).
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void evaluate_suspend_tenantAskida() {
        LocalDate now = LocalDate.now();
        Tenant t = newTenant(TenantStatus.AKTIF);
        craft(t.getId(), SubscriptionStatus.ODEME_BEKLIYOR, now.minusDays(20), now.minusDays(1),
                PaymentStatus.BEKLIYOR);

        service.evaluate(now);

        assertThat(subRepo.findByTenantId(t.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ASKIDA);
        // ⚠️ Kesinti: tenant ASKIDA (TenantStatusInterceptor is uclarini 403 ile keser).
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.ASKIDA);
    }

    @Test
    void evaluate_odendi_telafi() {
        LocalDate now = LocalDate.now();
        Tenant t = newTenant(TenantStatus.ASKIDA);
        craft(t.getId(), SubscriptionStatus.ASKIDA, now.minusDays(30), now.minusDays(10),
                PaymentStatus.ODENDI);

        service.evaluate(now);

        Subscription s = subRepo.findByTenantId(t.getId()).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(s.getGraceEndsAt()).isNull();
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void markPaid_askidaTenantiGeriAktifEder() {
        LocalDate now = LocalDate.now();
        Tenant t = newTenant(TenantStatus.ASKIDA);
        craft(t.getId(), SubscriptionStatus.ASKIDA, now.minusDays(30), now.minusDays(10),
                PaymentStatus.BEKLIYOR);

        SubscriptionResponse r = service.markPaid(t.getId(), now.plusDays(30));

        assertThat(r.status()).isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(r.paymentStatus()).isEqualTo(PaymentStatus.ODENDI);
        assertThat(r.currentPeriodEnd()).isEqualTo(now.plusDays(30));
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void warningFor_graceIcindeDolu_aktifteNull() {
        LocalDate now = LocalDate.now();
        Tenant grace = newTenant(TenantStatus.AKTIF);
        craft(grace.getId(), SubscriptionStatus.ODEME_BEKLIYOR, now.minusDays(2), now.plusDays(12),
                PaymentStatus.BEKLIYOR);
        Tenant aktif = newTenant(TenantStatus.AKTIF);
        craft(aktif.getId(), SubscriptionStatus.AKTIF, now.plusDays(300), null, PaymentStatus.ODENDI);

        SubscriptionWarning w = service.warningFor(grace.getId());
        assertThat(w).isNotNull();
        assertThat(w.inGrace()).isTrue();
        assertThat(w.graceEndsAt()).isEqualTo(now.plusDays(12));

        assertThat(service.warningFor(aktif.getId())).isNull();
    }

    @Test
    void evaluate_linaSeedBozulmaz() {
        service.evaluate(LocalDate.now());

        assertThat(subRepo.findByTenantId(LINA).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(tenantRepo.findById(LINA).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }
}
