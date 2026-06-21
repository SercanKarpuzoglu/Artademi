package com.artademi.platform;

import com.artademi.common.exception.NotFoundException;
import com.artademi.platform.dto.SubscriptionResponse;
import com.artademi.platform.dto.SubscriptionWarning;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abonelik durum-gecis is kurallari (platform-duzeyi).
 *
 * <p><b>Grace period mantigi (net ayrim):</b>
 * <ul>
 *   <li>Donem biter + odeme yok → {@code ODEME_BEKLIYOR} + {@code graceEndsAt = donemSonu + 14g}.
 *       Bu UYARI asamasidir; tenant.status AKTIF KALIR (erisim surer).</li>
 *   <li>Grace de gecer + odeme hala yok → abonelik {@code ASKIDA} + <b>tenant.status ASKIDA</b>.
 *       Bu KESINTI asamasidir; {@link com.artademi.common.tenant.TenantStatusInterceptor} is
 *       uclarini 403 ile keser.</li>
 *   <li>Odeme gelirse ({@code ODENDI}) → abonelik AKTIF + tenant AKTIF (telafi).</li>
 * </ul>
 *
 * <p>Bu sinif {@link com.artademi.common.tenant.TenantStatusInterceptor}'i DEGISTIRMEZ; yalnizca
 * tenant.status'u otomatik olarak ASKIDA'ya ceken/geri alan yeni bir yol ekler. Odeme entegrasyonu
 * YOK (payment_status elle set edilir).
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    /** Deneme suresi (gun). */
    static final int TRIAL_DAYS = 14;
    /** Grace (odeme bekleme) suresi (gun). */
    static final int GRACE_DAYS = 14;

    private final SubscriptionRepository subscriptions;
    private final TenantRepository tenants;

    public SubscriptionService(SubscriptionRepository subscriptions, TenantRepository tenants) {
        this.subscriptions = subscriptions;
        this.tenants = tenants;
    }

    /**
     * Yeni tenant icin DENEME aboneligi olusturur (provisioning'den cagrilir). Idempotent degil;
     * cift cagri UNIQUE(tenant_id) ihlali verir (cagiran try/catch ile tolere eder).
     */
    @Transactional
    public SubscriptionResponse createTrial(UUID tenantId) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.now(), TRIAL_DAYS);
        return SubscriptionResponse.from(subscriptions.save(s));
    }

    /**
     * TUM abonelikleri tarar ve durum gecislerini uygular (gunluk job + testler bunu DOGRUDAN cagirir;
     * deterministik). Her abonelik icin EN FAZLA bir gecis uygulanir.
     */
    @Transactional
    public void evaluate(LocalDate now) {
        for (Subscription s : subscriptions.findAll()) {
            boolean paid = s.getPaymentStatus() == PaymentStatus.ODENDI;

            // 1) Telafi: odeme gelmis ama abonelik hala bekliyor/askida ise AKTIF'e al + tenant'i ac.
            if (paid && (s.getStatus() == SubscriptionStatus.ODEME_BEKLIYOR
                    || s.getStatus() == SubscriptionStatus.ASKIDA)) {
                s.setStatus(SubscriptionStatus.AKTIF);
                s.setGraceEndsAt(null);
                reactivateTenant(s.getTenantId());
                continue;
            }

            // 2) Grace'e giris: aktif/deneme donemi bitti + odeme yok -> uyari (tenant AKTIF KALIR).
            boolean activeLike = s.getStatus() == SubscriptionStatus.AKTIF
                    || s.getStatus() == SubscriptionStatus.DENEME;
            if (activeLike && !paid && s.getCurrentPeriodEnd().isBefore(now)) {
                s.setStatus(SubscriptionStatus.ODEME_BEKLIYOR);
                s.setGraceEndsAt(s.getCurrentPeriodEnd().plusDays(GRACE_DAYS));
                continue;
            }

            // 3) Kesinti: grace de gecti + odeme yok -> abonelik ASKIDA + tenant ASKIDA.
            if (s.getStatus() == SubscriptionStatus.ODEME_BEKLIYOR && !paid
                    && s.getGraceEndsAt() != null && s.getGraceEndsAt().isBefore(now)) {
                s.setStatus(SubscriptionStatus.ASKIDA);
                suspendTenant(s.getTenantId());
            }
        }
    }

    /**
     * Odeme/donem gunceller (manuel; iyzico gelene kadar). {@code ODENDI} verilince telafi calisir:
     * abonelik AKTIF, grace temizlenir, tenant ASKIDA ise AKTIF'e doner. {@code currentPeriodEnd}
     * verilirse donem ilerletilir. Bilinmeyen tenant -> 404.
     */
    @Transactional
    public SubscriptionResponse applyPayment(UUID tenantId, PaymentStatus paymentStatus,
            LocalDate newPeriodEnd) {
        Subscription s = subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Abonelik bulunamadı: " + tenantId));

        s.setPaymentStatus(paymentStatus);
        if (newPeriodEnd != null) {
            s.setCurrentPeriodEnd(newPeriodEnd);
        }
        if (paymentStatus == PaymentStatus.ODENDI) {
            s.setStatus(SubscriptionStatus.AKTIF);
            s.setGraceEndsAt(null);
            reactivateTenant(tenantId); // telafi: erisim geri doner
        }
        return SubscriptionResponse.from(s);
    }

    /** {@link #applyPayment} kisayolu: odeme ODENDI isaretle (+ opsiyonel donem ilerlet). */
    @Transactional
    public SubscriptionResponse markPaid(UUID tenantId, LocalDate newPeriodEnd) {
        return applyPayment(tenantId, PaymentStatus.ODENDI, newPeriodEnd);
    }

    /** Bir tenant'in abonelik detayi; yoksa 404. */
    @Transactional(readOnly = true)
    public SubscriptionResponse getByTenant(UUID tenantId) {
        return subscriptions.findByTenantId(tenantId)
                .map(SubscriptionResponse::from)
                .orElseThrow(() -> new NotFoundException("Abonelik bulunamadı: " + tenantId));
    }

    /**
     * Iş kullanicisina gosterilecek grace uyarisi; abonelik {@code ODEME_BEKLIYOR} degilse null.
     * ({@code /api/me} icin — super.admin tenant'siz zaten buraya gelmez.)
     */
    @Transactional(readOnly = true)
    public SubscriptionWarning warningFor(UUID tenantId) {
        return subscriptions.findByTenantId(tenantId)
                .filter(s -> s.getStatus() == SubscriptionStatus.ODEME_BEKLIYOR)
                .map(s -> new SubscriptionWarning(true, s.getGraceEndsAt(),
                        "Ödemeniz alınamadı. Erişiminiz "
                                + s.getGraceEndsAt()
                                + " tarihine kadar açık; lütfen ödemenizi tamamlayın."))
                .orElse(null);
    }

    /** Verilen tenant'lar icin abonelik ozetlerini tenant id'ye gore haritalar (liste icin toplu). */
    @Transactional(readOnly = true)
    public Map<UUID, SubscriptionResponse> summariesByTenant(List<UUID> tenantIds) {
        Map<UUID, SubscriptionResponse> map = new LinkedHashMap<>();
        if (tenantIds.isEmpty()) {
            return map;
        }
        for (Subscription s : subscriptions.findByTenantIdIn(tenantIds)) {
            map.put(s.getTenantId(), SubscriptionResponse.from(s));
        }
        return map;
    }

    private void reactivateTenant(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(t -> t.setStatus(TenantStatus.AKTIF));
    }

    private void suspendTenant(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(t -> t.setStatus(TenantStatus.ASKIDA));
    }
}
