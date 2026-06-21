package com.artademi.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tenant aboneligi (1-1, platform-duzeyi).
 *
 * <p><b>⚠️ KRITIK:</b> {@link Tenant} gibi {@code TenantAware}'i GENISLETMEZ ve global tenant
 * filtresine TABI DEGILDIR. Buradaki {@code tenantId} hangi tenant'in aboneligi oldugunu soyleyen
 * bir FK'dir; izolasyon kolonu DEGIL. Bu yuzden {@code findByTenantId}/{@code findAll} burada
 * DOGRUDUR — {@code findScopedById} kuralinin istisnasidir (yalnizca platform mantigi okur).
 */
@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "grace_ends_at")
    private LocalDate graceEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // JPA icin
    }

    /**
     * Yeni tenant icin DENEME aboneligi: plan/status DENEME, donem {@code [today, today+trialDays]},
     * odeme BEKLIYOR, grace yok. (Provisioning sirasinda cagrilir.)
     */
    public static Subscription createTrial(UUID tenantId, LocalDate today, int trialDays) {
        Subscription s = new Subscription();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId;
        s.plan = Plan.DENEME;
        s.status = SubscriptionStatus.DENEME;
        s.currentPeriodStart = today;
        s.currentPeriodEnd = today.plusDays(trialDays);
        s.graceEndsAt = null;
        s.paymentStatus = PaymentStatus.BEKLIYOR;
        return s;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public LocalDate getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(LocalDate currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDate currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public LocalDate getGraceEndsAt() {
        return graceEndsAt;
    }

    public void setGraceEndsAt(LocalDate graceEndsAt) {
        this.graceEndsAt = graceEndsAt;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
