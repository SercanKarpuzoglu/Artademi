package com.artademi.billing.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Gonderilmis bir odeme uyarisinin izi (PLATFORM-DUZEYI; TenantAware DEGIL).
 * Salt-yazilir: setter YOKTUR — gonderilmis bildirim degistirilemez.
 */
@Entity
@Table(name = "billing_notification")
public class BillingNotification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 40, updatable = false)
    private BildirimTipi tip;

    @Column(name = "donem_anahtari", nullable = false, length = 40, updatable = false)
    private String donemAnahtari;

    @Column(name = "alici", length = 400, updatable = false)
    private String alici;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BillingNotification() {
        // JPA icin
    }

    public static BillingNotification of(UUID subscriptionId, UUID tenantId, BildirimTipi tip,
            String donemAnahtari, String alici) {
        BillingNotification n = new BillingNotification();
        n.id = UUID.randomUUID();
        n.subscriptionId = subscriptionId;
        n.tenantId = tenantId;
        n.tip = tip;
        n.donemAnahtari = donemAnahtari;
        n.alici = alici == null || alici.length() <= 400 ? alici : alici.substring(0, 399) + "…";
        return n;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public BildirimTipi getTip() {
        return tip;
    }

    public String getDonemAnahtari() {
        return donemAnahtari;
    }

    public String getAlici() {
        return alici;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
