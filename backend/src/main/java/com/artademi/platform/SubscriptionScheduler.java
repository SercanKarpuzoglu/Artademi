package com.artademi.platform;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gunluk abonelik degerlendirme job'i: donem/grace gecislerini ({@link SubscriptionService#evaluate})
 * otomatik uygular. Her gun 03:00'te calisir (dusuk trafik). Test'te scheduler'a GUVENILMEZ —
 * {@code evaluate()} dogrudan cagrilarak deterministik test edilir.
 */
@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionScheduler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** Her gun 03:00 — abonelik durum gecislerini uygula. */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        LocalDate today = LocalDate.now();
        log.info("Abonelik degerlendirmesi basliyor: {}", today);
        subscriptionService.evaluate(today);
    }
}
