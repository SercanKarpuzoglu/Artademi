package com.artademi.billing.notify;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bildirim izi repository (platform-duzeyi). Yalnizca INSERT + varlik kontrolu. */
public interface BillingNotificationRepository extends JpaRepository<BillingNotification, UUID> {

    /** Bu bildirim bu abonelige, bu donem icin daha once gonderildi mi? */
    boolean existsBySubscriptionIdAndTipAndDonemAnahtari(UUID subscriptionId, BildirimTipi tip,
            String donemAnahtari);
}
