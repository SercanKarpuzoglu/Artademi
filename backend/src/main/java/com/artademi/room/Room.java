package com.artademi.room;

import com.artademi.common.tenant.TenantAware;
import com.artademi.sube.Sube;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Salon tanim entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id}
 * ve global fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez
 * (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. RoomService).
 */
@Entity
@Table(name = "rooms")
public class Room extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Column(name = "kapasite")
    private Integer kapasite;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    /**
     * Salonun bulundugu sube. OPSIYONEL: tek subeli kurumlar sube tanimlamadan calisir.
     * Referans serviste {@code findScopedById} ile cozulur (capraz-tenant referans kurali).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sube_id")
    private Sube sube;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Room() {
        // JPA icin
    }

    /** Bos salon ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Room create() {
        return new Room();
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

    public Integer getKapasite() {
        return kapasite;
    }

    public void setKapasite(Integer kapasite) {
        this.kapasite = kapasite;
    }

    public Sube getSube() {
        return sube;
    }

    public void setSube(Sube sube) {
        this.sube = sube;
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
