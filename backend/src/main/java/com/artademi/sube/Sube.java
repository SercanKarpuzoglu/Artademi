package com.artademi.sube;

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
 * Sube (fiziksel lokasyon) tanim entity'si — "Kadikoy Subesi", "Merkez".
 *
 * <p>⚠️ {@code com.artademi.branch.Branch} ile KARISTIRILMAMALIDIR: o BRANS'tir
 * (Bale, Piyano). Sube bir adres, brans bir ders dalidir.
 *
 * <p>{@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant
 * filtresine otomatik tabidir; tenant_id ELLE yonetilmez.
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. SubeService).
 */
@Entity
@Table(name = "sube")
public class Sube extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Column(name = "adres", length = 500)
    private String adres;

    @Column(name = "telefon", length = 30)
    private String telefon;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Sube() {
        // JPA icin
    }

    /** Bos sube ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Sube create() {
        return new Sube();
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

    public String getAdres() {
        return adres;
    }

    public void setAdres(String adres) {
        this.adres = adres;
    }

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
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
