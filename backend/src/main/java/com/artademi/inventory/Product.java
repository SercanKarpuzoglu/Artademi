package com.artademi.inventory;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Filter;
import com.artademi.common.silme.SoftDeletable;

/**
 * Urun (product) is entity'si — satilabilir bir urun tanimi.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>PARA KURALI: {@code satisFiyati} {@link BigDecimal} (NUMERIC(12,2)), pozitif olmalidir
 * (DTO @Positive). {@code stokAdedi} adet oldugu icin {@code int} (>= 0). Asla double/float
 * kullanilmaz.
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. ProductService).
 * Stok ve aktiflik kendi uclarindan ({@code PATCH /stok}, {@code PATCH /active}) yonetilir;
 * normal guncelleme (PUT) bunlara DOKUNMAZ.
 */
@Entity
@Table(name = "product")
@Filter(name = SoftDeletable.FILTRE)
public class Product extends TenantAware implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Column(name = "satis_fiyati", precision = 12, scale = 2, nullable = false)
    private BigDecimal satisFiyati;

    /** Alis (maliyet) fiyati; opsiyonel (V30). Kar marji saklanmaz, ekranda satis - alis. */
    @Column(name = "alis_fiyati", precision = 12, scale = 2)
    private BigDecimal alisFiyati;

    @Column(name = "stok_adedi", nullable = false)
    private int stokAdedi;

    @Column(name = "aciklama")
    private String aciklama;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Product() {
        // JPA icin
    }

    /** Bos urun ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Product create() {
        return new Product();
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

    public BigDecimal getSatisFiyati() {
        return satisFiyati;
    }

    public void setSatisFiyati(BigDecimal satisFiyati) {
        this.satisFiyati = satisFiyati;
    }

    public BigDecimal getAlisFiyati() {
        return alisFiyati;
    }

    public void setAlisFiyati(BigDecimal alisFiyati) {
        this.alisFiyati = alisFiyati;
    }

    public int getStokAdedi() {
        return stokAdedi;
    }

    public void setStokAdedi(int stokAdedi) {
        this.stokAdedi = stokAdedi;
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
}
