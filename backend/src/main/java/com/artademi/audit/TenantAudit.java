package com.artademi.audit;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Kurum ici islem kaydi. TenantAware — bir kurumun yoneticisi YALNIZCA kendi kurumunun
 * kayitlarini gorur (Hibernate tenant filtresi otomatik uygular).
 *
 * <p>Salt-yazilir: setter YOKTUR. Denetim izinin degeri degistirilemez olmasindan gelir.
 */
@Entity
@Table(name = "tenant_audit")
public class TenantAudit extends TenantAware {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor", nullable = false, length = 120, updatable = false)
    private String actor;

    @Column(name = "actor_ad", length = 200, updatable = false)
    private String actorAd;

    @Column(name = "eylem", nullable = false, length = 120, updatable = false)
    private String eylem;

    @Column(name = "metot", nullable = false, length = 10, updatable = false)
    private String metot;

    @Column(name = "yol", nullable = false, length = 300, updatable = false)
    private String yol;

    @Column(name = "kayit_id", length = 80, updatable = false)
    private String kayitId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenantAudit() {
        // JPA icin
    }

    public static TenantAudit of(UUID tenantId, String actor, String actorAd, String eylem,
            String metot, String yol, String kayitId) {
        TenantAudit a = new TenantAudit();
        a.setTenantId(tenantId);
        a.id = UUID.randomUUID();
        a.actor = actor;
        a.actorAd = actorAd;
        a.eylem = eylem;
        a.metot = metot;
        a.yol = yol.length() <= 300 ? yol : yol.substring(0, 299);
        a.kayitId = kayitId;
        return a;
    }

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getActorAd() {
        return actorAd;
    }

    public String getEylem() {
        return eylem;
    }

    public String getMetot() {
        return metot;
    }

    public String getYol() {
        return yol;
    }

    public String getKayitId() {
        return kayitId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
