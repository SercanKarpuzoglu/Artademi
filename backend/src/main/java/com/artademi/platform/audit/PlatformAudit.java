package com.artademi.platform.audit;

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
 * Platform denetim kaydi (PLATFORM-DUZEYI; TenantAware DEGIL).
 *
 * <p>⚠️ SALT-YAZILIR: bu entity'nin setter'i YOKTUR — kayit olusturulduktan sonra degistirilemez.
 * Denetim izinin degeri degistirilemez olmasindan gelir.
 *
 * <p>{@code targetAd} bir SNAPSHOT'tur (FK degil): kurum silinse veya adi degisse bile iz
 * anlasilir kalir.
 */
@Entity
@Table(name = "platform_audit")
public class PlatformAudit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor", nullable = false, length = 120, updatable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Column(name = "target_tenant_id", updatable = false)
    private UUID targetTenantId;

    @Column(name = "target_ad", length = 200, updatable = false)
    private String targetAd;

    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformAudit() {
        // JPA icin
    }

    public static PlatformAudit of(String actor, AuditAction action, UUID targetTenantId,
            String targetAd, String detail) {
        PlatformAudit a = new PlatformAudit();
        a.id = UUID.randomUUID();
        a.actor = actor;
        a.action = action;
        a.targetTenantId = targetTenantId;
        a.targetAd = kirp(targetAd, 200);
        a.detail = kirp(detail, 500);
        return a;
    }

    /** Uzun metin denetim yazimini PATLATMAMALI — kolon sinirina kirpilir. */
    private static String kirp(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getTargetTenantId() {
        return targetTenantId;
    }

    public String getTargetAd() {
        return targetAd;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
