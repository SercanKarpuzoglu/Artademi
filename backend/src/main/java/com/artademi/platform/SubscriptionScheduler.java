package com.artademi.platform;

import com.artademi.billing.BillingReconciliationService;
import com.artademi.billing.notify.BillingNotificationService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gunluk abonelik job'i. Her gun 03:00'te calisir (dusuk trafik). Test'te scheduler'a GUVENILMEZ —
 * servis metodlari dogrudan cagrilarak deterministik test edilir.
 *
 * <p>⚠️ SIRA KRITIK: once MUTABAKAT (saglayicidaki gercek tahsilatlari yakala), sonra
 * DEGERLENDIRME (donem/grace gecisleri). Ters sirada, webhook'u kacmis ama parasi cekilmis bir
 * kurum ayni gun haksiz yere ASKIDA'ya alinirdi.
 */
@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionService subscriptionService;
    private final BillingReconciliationService reconciliation;
    private final BillingNotificationService notifications;

    public SubscriptionScheduler(SubscriptionService subscriptionService,
            BillingReconciliationService reconciliation,
            BillingNotificationService notifications) {
        this.subscriptionService = subscriptionService;
        this.reconciliation = reconciliation;
        this.notifications = notifications;
    }

    /**
     * Her gun 03:00. SIRA KRITIK:
     * <ol>
     *   <li><b>Mutabakat</b> — saglayicidaki gercek tahsilatlari yakala (odemis kurum askiya alinmasin)</li>
     *   <li><b>Degerlendirme</b> — donem/grace gecislerini uygula</li>
     *   <li><b>Bildirim</b> — GECISLERDEN SONRA uyar; boylece kuruma GUNCEL durum yazilir
     *       (or. bugun askiya alindiysa "askiya alindi" gider, "ek sureniz var" degil)</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        LocalDate today = LocalDate.now();
        log.info("Gunluk abonelik isi basliyor: {}", today);
        try {
            reconciliation.reconcileAll(today);
        } catch (RuntimeException e) {
            // Mutabakat patlasa bile degerlendirme calismali (grace/askiya alma durmasin).
            log.error("Mutabakat calistirilamadi: {}", e.getMessage());
        }
        subscriptionService.evaluate(today);
        try {
            notifications.bildirimleriGonder(today);
        } catch (RuntimeException e) {
            // Mail patlasa bile gunluk is basarili sayilir; abonelik durumlari zaten guncellendi.
            log.error("Odeme uyarilari gonderilemedi: {}", e.getMessage());
        }
    }
}
