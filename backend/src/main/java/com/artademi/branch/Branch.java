package com.artademi.branch;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Brans tanim entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id}
 * ve global fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez
 * (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. BranchService).
 */
@Entity
@Table(name = "branches")
public class Branch extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Branch() {
        // JPA icin
    }

    /** Bos brans ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Branch create() {
        return new Branch();
    }

    public Long getId() {
        return id;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public boolean isAktif() {
        return aktif;
    }

    public void setAktif(boolean aktif) {
        this.aktif = aktif;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
