package com.artademi.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.artademi.billing.dto.ProviderSubscriptionState;
import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Mutabakat testleri — asil senaryo: WEBHOOK KACTI ama para cekildi. Sistem bunu saglayiciya
 * sorarak yakalamali ve odeme yapan kurumu askiya ALMAMALI.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingReconciliationServiceTest {

    private static final LocalDate BUGUN = LocalDate.of(2026, 8, 15);

    @Mock
    PaymentProvider provider;

    @Mock
    SubscriptionRepository subscriptions;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    BillingService billingService;

    BillingReconciliationService service;
    UUID tenantId;
    Subscription sub;

    @BeforeEach
    void setUp() {
        service = new BillingReconciliationService(provider, subscriptions, subscriptionService,
                billingService);
        tenantId = UUID.randomUUID();
        sub = Subscription.createTrial(tenantId, LocalDate.of(2026, 7, 1), 14);
        sub.setProviderSubscriptionRef("SUB-1");
        given(subscriptions.findByProviderSubscriptionRefIsNotNull()).willReturn(List.of(sub));
    }

    @Test
    void kacanTahsilat_yakalanir_donemIlerler() {
        // Saglayicida donem 15 Eylul'e kadar odenmis; bizim kayit 15 Temmuz'da kalmis (webhook kacti).
        sub.setCurrentPeriodEnd(LocalDate.of(2026, 7, 15));
        given(provider.fetchSubscriptionState("SUB-1")).willReturn(Optional.of(
                new ProviderSubscriptionState(true, true, LocalDate.of(2026, 9, 15))));

        int guncellenen = service.reconcileAll(BUGUN);

        assertThat(guncellenen).isEqualTo(1);
        verify(subscriptionService).markPaid(tenantId, LocalDate.of(2026, 9, 15));
    }

    @Test
    void kayitZatenGuncel_dokunmaz() {
        sub.setCurrentPeriodEnd(LocalDate.of(2026, 9, 15));
        given(provider.fetchSubscriptionState("SUB-1")).willReturn(Optional.of(
                new ProviderSubscriptionState(true, true, LocalDate.of(2026, 9, 15))));

        assertThat(service.reconcileAll(BUGUN)).isZero();
        verify(subscriptionService, never()).markPaid(any(), any());
    }

    @Test
    void saglayiciSorgulanamadi_kaydaDOKUNMAZ() {
        // Fail-safe: iyzico'ya ulasilamadi diye kimseyi askiya almayiz/odenmis saymayiz.
        sub.setCurrentPeriodEnd(LocalDate.of(2026, 7, 15));
        given(provider.fetchSubscriptionState("SUB-1")).willReturn(Optional.empty());

        assertThat(service.reconcileAll(BUGUN)).isZero();
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void saglayicidaIptal_donemBitmis_odemeBekliyorYapilir() {
        sub.setCurrentPeriodEnd(LocalDate.of(2026, 8, 1)); // gecmis
        sub.setPaymentStatus(PaymentStatus.ODENDI);
        given(provider.fetchSubscriptionState("SUB-1")).willReturn(Optional.of(
                new ProviderSubscriptionState(false, false, null)));

        assertThat(service.reconcileAll(BUGUN)).isEqualTo(1);
        verify(subscriptionService).applyPayment(tenantId, PaymentStatus.BEKLIYOR, null);
    }

    @Test
    void saglayicidaIptal_donemDevamEdiyor_erkenKesintiYAPMAZ() {
        // Iptal edilmis olsa bile odenmis donem bitene kadar erisim surer (adil davranis).
        sub.setCurrentPeriodEnd(LocalDate.of(2026, 12, 1)); // gelecek
        sub.setPaymentStatus(PaymentStatus.ODENDI);
        given(provider.fetchSubscriptionState("SUB-1")).willReturn(Optional.of(
                new ProviderSubscriptionState(false, false, null)));

        assertThat(service.reconcileAll(BUGUN)).isZero();
        verify(subscriptionService, never()).applyPayment(any(), any(), any());
    }

    @Test
    void birAbonelikPatlarsa_digerleriIslenmeyeDevamEder() {
        Subscription ikinci = Subscription.createTrial(UUID.randomUUID(), LocalDate.of(2026, 7, 1), 14);
        ikinci.setProviderSubscriptionRef("SUB-2");
        ikinci.setCurrentPeriodEnd(LocalDate.of(2026, 7, 15));
        given(subscriptions.findByProviderSubscriptionRefIsNotNull())
                .willReturn(List.of(sub, ikinci));
        given(provider.fetchSubscriptionState("SUB-1"))
                .willThrow(new RuntimeException("iyzico patladı"));
        given(provider.fetchSubscriptionState("SUB-2")).willReturn(Optional.of(
                new ProviderSubscriptionState(true, true, LocalDate.of(2026, 9, 15))));

        assertThat(service.reconcileAll(BUGUN)).isEqualTo(1);
        verify(subscriptionService).markPaid(eq(ikinci.getTenantId()), any());
    }
}
