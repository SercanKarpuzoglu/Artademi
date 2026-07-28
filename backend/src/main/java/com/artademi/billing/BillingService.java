package com.artademi.billing;

import com.artademi.billing.dto.BillingSubscriptionResponse;
import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.billing.dto.IyzicoWebhookPayload;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionService;
import com.artademi.platform.SubscriptionStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Odeme akisi is kurallari (platform geliri — tenant'in IC muhasebesiyle ilgisi YOK,
 * bkz. subscription-billing skill).
 *
 * <p>Akis: ADMIN kendi tenant'i icin checkout baslatir → iyzico hosted formda kart girer →
 * callback token'i dogrular ve abonelik referanslarini baglar (ilk tahsilat ODENDI) → sonraki
 * aylik tahsilatlari iyzico kendi zamanlar, sonucu webhook ile bildirir → webhook paymentStatus'u
 * gunceller; grace/ASKIDA gecislerini mevcut {@link SubscriptionService#evaluate} yonetir.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    /** iyzico abonelik webhook event tipleri. */
    static final String EVENT_ORDER_SUCCESS = "subscription.order.success";
    static final String EVENT_ORDER_FAILURE = "subscription.order.failure";

    private final PaymentProvider provider;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionService subscriptionService;
    private final BillingEventRepository events;

    public BillingService(PaymentProvider provider, SubscriptionRepository subscriptions,
            SubscriptionService subscriptionService, BillingEventRepository events) {
        this.provider = provider;
        this.subscriptions = subscriptions;
        this.subscriptionService = subscriptionService;
        this.events = events;
    }

    /** Aktif tenant'in abonelik ozeti + otomatik odeme bagi (odeme sayfasi bunu gosterir). */
    @Transactional(readOnly = true)
    public BillingSubscriptionResponse ownSubscription() {
        Subscription s = requireOwnSubscription();
        return BillingSubscriptionResponse.from(s);
    }

    /**
     * Aktif tenant icin iyzico checkout'u baslatir; token abonelige yazilir (callback eslesmesi).
     * Zaten saglayiciya bagli AKTIF abonelik varsa 409 (cift abonelik/cift tahsilat onlenir).
     */
    @Transactional
    public CheckoutSession startCheckout(CheckoutCustomer customer) {
        Subscription s = requireOwnSubscription();
        if (s.getProviderSubscriptionRef() != null && s.getStatus() == SubscriptionStatus.AKTIF
                && s.getPaymentStatus() == PaymentStatus.ODENDI) {
            throw new ConflictException(
                    "Zaten aktif bir otomatik ödeme aboneliğiniz var.");
        }
        CheckoutSession session = provider.startCheckout(customer);
        s.setCheckoutToken(session.token());
        return session;
    }

    /**
     * iyzico callback'i: token'in sonucunu SAGLAYICIDAN dogrular (client verisine guvenilmez).
     * Basarili ise abonelik referanslarini baglar, plani AYLIK'a cevirir ve ilk donemi bugunden
     * 1 ay ileri kurar (ODENDI → tenant ASKIDA idiyse telafi ile acilir).
     *
     * @return true = basarili (web'e "basarili" redirect'i icin)
     */
    @Transactional
    public boolean completeCheckout(String token) {
        Subscription s = subscriptions.findByCheckoutToken(token)
                .orElseThrow(() -> new NotFoundException("Checkout bulunamadı: " + token));
        s.setCheckoutToken(null); // tek kullanimlik; tekrar denemede yeni checkout baslatilir

        CheckoutResult result = provider.fetchCheckoutResult(token);
        if (!result.success()) {
            log.warn("iyzico checkout başarısız (tenant={}, token={})", s.getTenantId(), token);
            return false;
        }
        s.setProvider(provider.name());
        s.setProviderSubscriptionRef(result.subscriptionReferenceCode());
        s.setProviderCustomerRef(result.customerReferenceCode());
        s.setPlan(Plan.AYLIK);
        s.setCurrentPeriodStart(LocalDate.now());
        subscriptionService.markPaid(s.getTenantId(), LocalDate.now().plusMonths(1));
        log.info("iyzico aboneliği bağlandı: tenant={}, subRef={}",
                s.getTenantId(), result.subscriptionReferenceCode());
        return true;
    }

    /**
     * iyzico abonelik webhook'unu isler. Imza dogrulamasi CONTROLLER'da yapilmistir; burada:
     * (1) idempotency — ayni bildirim (eventType+orderRef) ikinci kez islenmez;
     * (2) subscriptionReferenceCode ile abonelik bulunur, bulunamazsa IGNORED kaydedilir
     *     (200 doneriz ki iyzico bosuna tekrarlamasin);
     * (3) success → ODENDI + donem 1 ay ilerler; failure → BASARISIZ (grace/ASKIDA gecisini
     *     gunluk {@code evaluate} yonetir — burada erken kesinti YAPILMAZ).
     */
    @Transactional
    public void handleIyzicoWebhook(IyzicoWebhookPayload payload, String rawBody) {
        String dedupKey = payload.iyziEventType() + ":" + payload.orderReferenceCode();
        if (events.existsByProviderAndDedupKey(provider.name(), dedupKey)) {
            log.info("Webhook mükerrer, atlandı: {}", dedupKey);
            return;
        }

        Subscription s = subscriptions
                .findByProviderSubscriptionRef(payload.subscriptionReferenceCode())
                .orElse(null);
        if (s == null) {
            log.warn("Webhook eşleşmedi (subRef={}), IGNORED", payload.subscriptionReferenceCode());
            events.save(BillingEvent.of(provider.name(), payload.iyziEventType(), dedupKey,
                    null, null, rawBody, BillingEvent.Status.IGNORED));
            return;
        }

        UUID tenantId = s.getTenantId();
        switch (payload.iyziEventType()) {
            case EVENT_ORDER_SUCCESS -> {
                s.setCurrentPeriodStart(LocalDate.now());
                subscriptionService.markPaid(tenantId, LocalDate.now().plusMonths(1));
            }
            case EVENT_ORDER_FAILURE ->
                    subscriptionService.applyPayment(tenantId, PaymentStatus.BASARISIZ, null);
            default -> {
                log.info("İlgisiz webhook tipi, IGNORED: {}", payload.iyziEventType());
                events.save(BillingEvent.of(provider.name(), payload.iyziEventType(), dedupKey,
                        s.getId(), tenantId, rawBody, BillingEvent.Status.IGNORED));
                return;
            }
        }
        events.save(BillingEvent.of(provider.name(), payload.iyziEventType(), dedupKey,
                s.getId(), tenantId, rawBody, BillingEvent.Status.PROCESSED));
    }

    private Subscription requireOwnSubscription() {
        UUID tenantId = TenantContext.get();
        return subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Abonelik bulunamadı: " + tenantId));
    }
}
