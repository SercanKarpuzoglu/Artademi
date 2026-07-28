package com.artademi.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.billing.dto.IyzicoWebhookPayload;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionService;
import com.artademi.platform.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * BillingService birim testleri: checkout baslatma/koruma, callback baglama, webhook idempotency
 * ve success/failure gecisleri. Saglayici MOCK (ag yok).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock
    PaymentProvider provider;

    @Mock
    SubscriptionRepository subscriptions;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    BillingEventRepository events;

    BillingService service;

    UUID tenantId;
    Subscription sub;

    static final CheckoutCustomer CUSTOMER = new CheckoutCustomer("Ada", "Yılmaz",
            "ada@ornek.com", "+905551112233", "12345678901", "Adres 1", "İstanbul", "Türkiye");

    @BeforeEach
    void setUp() {
        service = new BillingService(provider, subscriptions, subscriptionService, events);
        tenantId = UUID.randomUUID();
        sub = Subscription.createTrial(tenantId, LocalDate.now(), 14);
        TenantContext.set(tenantId);
        given(provider.name()).willReturn("iyzico");
        given(subscriptions.findByTenantId(tenantId)).willReturn(Optional.of(sub));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void startCheckout_tokenAbonelijeYazilir() {
        given(provider.startCheckout(CUSTOMER))
                .willReturn(new CheckoutSession("tok-1", "<form/>"));

        CheckoutSession session = service.startCheckout(CUSTOMER);

        assertThat(session.token()).isEqualTo("tok-1");
        assertThat(sub.getCheckoutToken()).isEqualTo("tok-1");
    }

    @Test
    void startCheckout_zatenAktifBagliAbonelik_409() {
        sub.setProviderSubscriptionRef("SUB-ESKI");
        sub.setStatus(SubscriptionStatus.AKTIF);
        sub.setPaymentStatus(PaymentStatus.ODENDI);

        assertThatThrownBy(() -> service.startCheckout(CUSTOMER))
                .isInstanceOf(ConflictException.class);
        verify(provider, never()).startCheckout(any());
    }

    @Test
    void completeCheckout_basarili_baglarVeOdenmisIsaretler() {
        sub.setCheckoutToken("tok-1");
        given(subscriptions.findByCheckoutToken("tok-1")).willReturn(Optional.of(sub));
        given(provider.fetchCheckoutResult("tok-1"))
                .willReturn(new CheckoutResult(true, "SUB-YENI", "CUST-1"));

        boolean success = service.completeCheckout("tok-1");

        assertThat(success).isTrue();
        assertThat(sub.getProvider()).isEqualTo("iyzico");
        assertThat(sub.getProviderSubscriptionRef()).isEqualTo("SUB-YENI");
        assertThat(sub.getProviderCustomerRef()).isEqualTo("CUST-1");
        assertThat(sub.getPlan()).isEqualTo(Plan.AYLIK);
        assertThat(sub.getCheckoutToken()).isNull(); // tek kullanimlik
        verify(subscriptionService).markPaid(eq(tenantId), any(LocalDate.class));
    }

    @Test
    void completeCheckout_saglayiciBasarisiz_falseVeBaglamaz() {
        sub.setCheckoutToken("tok-1");
        given(subscriptions.findByCheckoutToken("tok-1")).willReturn(Optional.of(sub));
        given(provider.fetchCheckoutResult("tok-1"))
                .willReturn(new CheckoutResult(false, null, null));

        assertThat(service.completeCheckout("tok-1")).isFalse();
        assertThat(sub.getProviderSubscriptionRef()).isNull();
        assertThat(sub.getCheckoutToken()).isNull(); // yine temizlenir; yeni checkout acilir
        verify(subscriptionService, never()).markPaid(any(), any());
    }

    @Test
    void webhook_mukerrerBildirim_islenmez() {
        given(events.existsByProviderAndDedupKey("iyzico",
                "subscription.order.success:ORD-1")).willReturn(true);

        service.handleIyzicoWebhook(payload("subscription.order.success", "SUB-1", "ORD-1"), "{}");

        verifyNoInteractions(subscriptionService);
        verify(events, never()).save(any());
    }

    @Test
    void webhook_eslesmeyenAbonelik_IGNOREDKaydedilir() {
        given(subscriptions.findByProviderSubscriptionRef("SUB-YOK"))
                .willReturn(Optional.empty());

        service.handleIyzicoWebhook(payload("subscription.order.success", "SUB-YOK", "ORD-1"),
                "{}");

        verifyNoInteractions(subscriptionService);
        ArgumentCaptor<BillingEvent> saved = ArgumentCaptor.forClass(BillingEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingEvent.Status.IGNORED);
    }

    @Test
    void webhook_success_odendiVeDonemIlerler() {
        sub.setProviderSubscriptionRef("SUB-1");
        given(subscriptions.findByProviderSubscriptionRef("SUB-1")).willReturn(Optional.of(sub));

        service.handleIyzicoWebhook(payload("subscription.order.success", "SUB-1", "ORD-1"),
                "{}");

        verify(subscriptionService).markPaid(eq(tenantId), any(LocalDate.class));
        ArgumentCaptor<BillingEvent> saved = ArgumentCaptor.forClass(BillingEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingEvent.Status.PROCESSED);
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void webhook_failure_basarisizIsaretlenir_kesintiEvaluateYapar() {
        sub.setProviderSubscriptionRef("SUB-1");
        given(subscriptions.findByProviderSubscriptionRef("SUB-1")).willReturn(Optional.of(sub));

        service.handleIyzicoWebhook(payload("subscription.order.failure", "SUB-1", "ORD-2"),
                "{}");

        verify(subscriptionService).applyPayment(tenantId, PaymentStatus.BASARISIZ, null);
        verify(subscriptionService, never()).markPaid(any(), any());
    }

    private static IyzicoWebhookPayload payload(String type, String subRef, String orderRef) {
        return new IyzicoWebhookPayload(type, 1700000000L, "IYZ-1", subRef, orderRef, "CUST-1");
    }
}
