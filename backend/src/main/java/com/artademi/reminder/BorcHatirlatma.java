package com.artademi.reminder;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Veliye gonderilmis bir borc hatirlatmasinin izi (TenantAware — kurumlar birbirininkini gormez).
 * Salt-yazilir: gonderilmis bildirim degistirilemez.
 */
@Entity
@Table(name = "borc_hatirlatma")
public class BorcHatirlatma extends TenantAware {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ogrenci_id", nullable = false, updatable = false)
    private Long ogrenciId;

    @Column(name = "tutar", nullable = false, updatable = false)
    private BigDecimal tutar;

    @Column(name = "alici", nullable = false, length = 200, updatable = false)
    private String alici;

    @Column(name = "gonderen", nullable = false, length = 120, updatable = false)
    private String gonderen;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BorcHatirlatma() {
        // JPA icin
    }

    public static BorcHatirlatma of(Long ogrenciId, BigDecimal tutar, String alici,
            String gonderen) {
        BorcHatirlatma b = new BorcHatirlatma();
        b.id = UUID.randomUUID();
        b.ogrenciId = ogrenciId;
        b.tutar = tutar;
        b.alici = alici;
        b.gonderen = gonderen;
        return b;
    }

    public UUID getId() {
        return id;
    }

    public Long getOgrenciId() {
        return ogrenciId;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public String getAlici() {
        return alici;
    }

    public String getGonderen() {
        return gonderen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
