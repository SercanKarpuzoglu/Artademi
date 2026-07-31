package com.artademi.billing;

import com.artademi.billing.dto.ProviderSubscriptionState;
import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Odeme MUTABAKATI — saglayicidaki gercek durumu kendi kaydimizla eslestirir.
 *
 * <p><b>Neden var:</b> webhook teslimi garanti degil. Sandbox'ta hic gelmedigi olcumle goruldu
 * (basarili tahsilata ragmen bildirim yok); canlida da ag/gecici hata ile kacabilir. Webhook
 * yalnizca "hizli yol"dur; DOGRULUK bu isten gelir. Aksi halde en kotu senaryo gerceklesir:
 * iyzico parayi ceker, biz gormeyiz, odeme yapan kurumu ASKIDA'ya aliriz.
 *
 * <p>Gunluk is sirasi kritik: mutabakat {@link SubscriptionService#evaluate} ÖNCESİNDE calisir
 * ki ayni gun icinde odenmis abonelik yanlislikla askiya alinmasin.
 */
@Service
public class BillingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BillingReconciliationService.class);

    private final PaymentProvider provider;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionService subscriptionService;

    public BillingReconciliationService(PaymentProvider provider,
            SubscriptionRepository subscriptions, SubscriptionService subscriptionService) {
        this.provider = provider;
        this.subscriptions = subscriptions;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Saglayiciya bagli tum abonelikleri mutabakattan gecirir.
     *
     * <p>Tek bir aboneligin hatasi digerlerini DURDURMAZ (bir tenant'in sorunu tum platformun
     * mutabakatini engellememeli). Deterministik test icin {@code today} disaridan verilir.
     *
     * @return guncellenen abonelik sayisi
     */
    @Transactional
    public int reconcileAll(LocalDate today) {
        List<Subscription> bagliOlanlar = subscriptions.findByProviderSubscriptionRefIsNotNull();
        int guncellenen = 0;
        for (Subscription s : bagliOlanlar) {
            try {
                if (reconcileOne(s, today)) {
                    guncellenen++;
                }
            } catch (RuntimeException e) {
                // Yut ve devam: sonraki abonelikler islenmeye devam etsin.
                log.error("Mutabakat hatası (tenant={}): {}", s.getTenantId(), e.getMessage());
            }
        }
        log.info("Mutabakat bitti: {} abonelik kontrol edildi, {} güncellendi",
                bagliOlanlar.size(), guncellenen);
        return guncellenen;
    }

    /** @return kayit guncellendiyse true */
    private boolean reconcileOne(Subscription s, LocalDate today) {
        Optional<ProviderSubscriptionState> durumOpt =
                provider.fetchSubscriptionState(s.getProviderSubscriptionRef());
        if (durumOpt.isEmpty()) {
            return false; // saglayici sorgulanamadi → mevcut kayda DOKUNMA (fail-safe)
        }
        ProviderSubscriptionState durum = durumOpt.get();

        // Odenmis donem bizim kaydimizdan ILERIDEYSE: tahsilat olmus ama biz gormemisiz
        // (webhook kacmis) → donemi ilerlet + ODENDI. markPaid ASKIDA'dan telafiyi de yapar.
        if (durum.sonOdemeBasarili() && durum.odenmisDonemSonu() != null
                && ileride(durum.odenmisDonemSonu(), s.getCurrentPeriodEnd())) {
            log.info("Mutabakat: kaçan tahsilat yakalandı (tenant={}, dönem {} → {})",
                    s.getTenantId(), s.getCurrentPeriodEnd(), durum.odenmisDonemSonu());
            subscriptionService.markPaid(s.getTenantId(), durum.odenmisDonemSonu());
            return true;
        }

        // Saglayicida abonelik artik aktif degil (iptal/odenmemis) ve biz hala ODENDI sayiyoruz:
        // donem sonunu bekle, ama odeme durumunu duzelt ki evaluate dogru karar versin.
        if (!durum.aktif() && s.getPaymentStatus() == PaymentStatus.ODENDI
                && s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(today)) {
            log.info("Mutabakat: sağlayıcıda abonelik pasif, ödeme bekliyor (tenant={})",
                    s.getTenantId());
            subscriptionService.applyPayment(s.getTenantId(), PaymentStatus.BEKLIYOR, null);
            return true;
        }
        return false;
    }

    private static boolean ileride(LocalDate saglayiciBitis, LocalDate bizimBitis) {
        return bizimBitis == null || saglayiciBitis.isAfter(bizimBitis);
    }
}
