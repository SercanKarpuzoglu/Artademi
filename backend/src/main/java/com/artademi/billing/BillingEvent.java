package com.artademi.billing;

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
 * Webhook denetim izi + idempotency kaydi (PLATFORM-DUZEYI; TenantAware DEGIL — V13/V14 gibi).
 * {@code UNIQUE(provider, dedupKey)} sayesinde iyzico'nun tekrarladigi bildirim ikinci kez islenmez.
 */
@Entity
@Table(name = "billing_event")
public class BillingEvent {

    public enum Status { PROCESSED, IGNORED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "dedup_key", nullable = false, length = 160)
    private String dedupKey;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BillingEvent() {
        // JPA icin
    }

    public static BillingEvent of(String provider, String eventType, String dedupKey,
            UUID subscriptionId, UUID tenantId, String payload, Status status) {
        BillingEvent e = new BillingEvent();
        e.id = UUID.randomUUID();
        e.provider = provider;
        e.eventType = eventType;
        e.dedupKey = dedupKey;
        e.subscriptionId = subscriptionId;
        e.tenantId = tenantId;
        e.payload = payload;
        e.status = status;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
