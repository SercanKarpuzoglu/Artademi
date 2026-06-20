package com.artademi.platform;

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
 * Kiraci (tenant) — birinci-sinif platform entity'si.
 *
 * <p><b>⚠️ KRITIK:</b> Bu entity {@code TenantAware}'i GENISLETMEZ ve global tenant filtresine TABI
 * DEGILDIR. Tenant kaydi tenant'larin USTUNDEdir; filtreye girerse kendi kaydini filtreleyip
 * gorunmez yapardi (klasik tuzak). PK, JWT'deki {@code tenant_id} ile birebir ayni UUID'dir;
 * uygulama bir tenant'i yalnizca kendi {@code TenantContext} id'siyle okur (id parametresi disaridan
 * alinmaz).
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ad", nullable = false, length = 200)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status = TenantStatus.AKTIF;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tenant() {
        // JPA icin
    }

    public UUID getId() {
        return id;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
