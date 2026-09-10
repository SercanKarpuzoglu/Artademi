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
import org.hibernate.annotations.Filter;
import com.artademi.common.silme.SoftDeletable;
import com.artademi.donem.Donem;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Brans tanim entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id}
 * ve global fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez
 * (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. BranchService).
 */
@Entity
@Table(name = "branches")
@Filter(name = SoftDeletable.FILTRE)
public class Branch extends TenantAware implements SoftDeletable {

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

    // --- Yumusak silme (V32): bkz. SoftDeletable. Sorgularda @SQLRestriction ile gizlenir. ---
    @Column(name = "silindi_tarihi")
    private Instant silindiTarihi;

    @Column(name = "silen", length = 100)
    private String silen;

    @Override
    public Instant getSilindiTarihi() {
        return silindiTarihi;
    }

    @Override
    public void setSilindiTarihi(Instant silindiTarihi) {
        this.silindiTarihi = silindiTarihi;
    }

    public String getSilen() {
        return silen;
    }

    @Override
    public void setSilen(String silen) {
        this.silen = silen;
    }

    // --- V36: bransin VARSAYILAN donemi. Kredi hesabi GRUBUN donemine bakar; bu alan yeni grup
    // acilirken on-doldurma icindir (grupta degistirilebilir). ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donem_id")
    private Donem donem;

    public Donem getDonem() {
        return donem;
    }

    public void setDonem(Donem donem) {
        this.donem = donem;
    }
}
