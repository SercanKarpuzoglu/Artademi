package com.artademi.kasa;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Nakit kasasi veya banka hesabi.
 *
 * <p>⚠️ Bakiye BURADA SAKLANMAZ. Saklanan bakiye zamanla gercekten sapar (bir tahsilat elle
 * duzeltilir, bir gider silinir, guncelleme kacar). Bakiye her zaman hareketlerden hesaplanir
 * (bkz. {@code KasaService.bakiye}).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} ile pasiflestirilir — gecmis tahsilat ve
 * giderler o kasaya bagli kalmaya devam eder.
 */
@Entity
@Table(name = "kasa")
public class Kasa extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 20)
    private KasaTipi tip = KasaTipi.NAKIT;

    @Column(name = "iban", length = 34)
    private String iban;

    /** Sisteme gecmeden onceki mevcut bakiye; gecmis hareketleri girmeye gerek kalmasin. */
    @Column(name = "acilis_bakiyesi", precision = 14, scale = 2, nullable = false)
    private BigDecimal acilisBakiyesi = BigDecimal.ZERO;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Kasa() {
        // JPA icin
    }

    public static Kasa create() {
        return new Kasa();
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

    public KasaTipi getTip() {
        return tip;
    }

    public void setTip(KasaTipi tip) {
        this.tip = tip;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public BigDecimal getAcilisBakiyesi() {
        return acilisBakiyesi;
    }

    public void setAcilisBakiyesi(BigDecimal acilisBakiyesi) {
        this.acilisBakiyesi = acilisBakiyesi;
    }

    public boolean isAktif() {
        return aktif;
    }

    public void setAktif(boolean aktif) {
        this.aktif = aktif;
    }
}
