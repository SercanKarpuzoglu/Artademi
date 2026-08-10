package com.artademi.platform;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Abonelik yasam dongusunun yeni kurallari: SUPER_ADMIN muafiyeti ve donem-sonunda iptal.
 *
 * <p>En kritik iki davranis:
 * <ul>
 *   <li>Muaf abonelik gece isi tarafindan ASLA askiya alinmaz (super admin'in karari sessizce
 *       geri alinmasin).</li>
 *   <li>Iptal eden kurumun erisimi donem sonuna kadar SURER (odedigi hizmet kesilmez).</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class SubscriptionLifecycleTest {

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

    private static final LocalDate BUGUN = LocalDate.of(2026, 9, 20);

    private Tenant tenant(TenantStatus st) {
        Tenant t = Tenant.create("Yasam " + UUID.randomUUID());
        t.setStatus(st);
        return tenantRepo.save(t);
    }

    private Subscription sub(UUID tenantId, SubscriptionStatus st, PaymentStatus pay,
            LocalDate periodEnd, LocalDate graceEnd) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.of(2026, 8, 1), 14);
        s.setStatus(st);
        s.setPaymentStatus(pay);
        s.setCurrentPeriodEnd(periodEnd);
        s.setGraceEndsAt(graceEnd);
        return subRepo.save(s);
    }

    // ---------- muafiyet ----------

    @Test
    void muafAbonelik_odemeYokSaBile_gunlukIsAskiyaALMAZ() {
        Tenant t = tenant(TenantStatus.AKTIF);
        // Ek sure dolmus, odeme yok → normalde ASKIDA'ya duserdi.
        Subscription s = sub(t.getId(), SubscriptionStatus.ODEME_BEKLIYOR, PaymentStatus.BEKLIYOR,
                BUGUN.minusDays(30), BUGUN.minusDays(5));
        service.setMuafiyet(t.getId(), true, "Sözleşmeli müşteri — fatura ile ödüyor");

        service.evaluate(BUGUN);

        Subscription sonra = subRepo.findById(s.getId()).orElseThrow();
        assertThat(sonra.getStatus()).isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void muafiyetAcilinca_askidakiKurumHemenAcilir() {
        Tenant t = tenant(TenantStatus.ASKIDA);
        sub(t.getId(), SubscriptionStatus.ASKIDA, PaymentStatus.BEKLIYOR,
                BUGUN.minusDays(40), BUGUN.minusDays(20));

        service.setMuafiyet(t.getId(), true, "Demo hesabı");

        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void muafiyetKapatilinca_gunlukIsTekrarIsler() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = sub(t.getId(), SubscriptionStatus.ODEME_BEKLIYOR, PaymentStatus.BEKLIYOR,
                BUGUN.minusDays(30), BUGUN.minusDays(5));
        service.setMuafiyet(t.getId(), true, "gecici");
        service.setMuafiyet(t.getId(), false, null);
        // Muafiyet acilirken AKTIF'e alinmisti; tekrar grace'e dusurup senaryoyu kur.
        Subscription g = subRepo.findById(s.getId()).orElseThrow();
        g.setStatus(SubscriptionStatus.ODEME_BEKLIYOR);
        g.setGraceEndsAt(BUGUN.minusDays(5));
        subRepo.save(g);

        service.evaluate(BUGUN);

        assertThat(subRepo.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ASKIDA);
    }

    // ---------- donem sonunda iptal ----------

    @Test
    void iptalEdilen_donemSonunaKadarERISEBILIR() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = sub(t.getId(), SubscriptionStatus.AKTIF, PaymentStatus.ODENDI,
                BUGUN.plusDays(10), null);
        service.setCancelAtPeriodEnd(t.getId(), true);

        service.evaluate(BUGUN);

        // Donem daha bitmedi → hizmet SURER (odenmis hizmet kesilmez).
        assertThat(subRepo.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.AKTIF);
    }

    @Test
    void iptalEdilen_donemBitince_IPTALveErisimKapanir() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = sub(t.getId(), SubscriptionStatus.AKTIF, PaymentStatus.ODENDI,
                BUGUN.minusDays(1), null);
        service.setCancelAtPeriodEnd(t.getId(), true);

        service.evaluate(BUGUN);

        assertThat(subRepo.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.IPTAL);
        assertThat(tenantRepo.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.ASKIDA);
    }

    @Test
    void iptalGeriAlinabilir_donemBitmedenOnce() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = sub(t.getId(), SubscriptionStatus.AKTIF, PaymentStatus.ODENDI,
                BUGUN.plusDays(5), null);
        service.setCancelAtPeriodEnd(t.getId(), true);
        assertThat(subRepo.findById(s.getId()).orElseThrow().getCanceledAt()).isNotNull();

        service.setCancelAtPeriodEnd(t.getId(), false);

        Subscription geri = subRepo.findById(s.getId()).orElseThrow();
        assertThat(geri.isCancelAtPeriodEnd()).isFalse();
        assertThat(geri.getCanceledAt()).isNull();

        service.evaluate(BUGUN.plusDays(10));
        // Iptal geri alindigi icin donem gecse de IPTAL olmaz (odeme akisina birakilir).
        assertThat(subRepo.findById(s.getId()).orElseThrow().getStatus())
                .isNotEqualTo(SubscriptionStatus.IPTAL);
    }
}
