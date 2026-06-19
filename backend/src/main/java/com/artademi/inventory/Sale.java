package com.artademi.inventory;

import com.artademi.common.tenant.TenantAware;
import com.artademi.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Satis (sale) is entity'si — bir urunun satilmasi.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code urun} ZORUNLU; {@code ogrenci} OPSIYONEL. Referanslarin baska tenant'a ait olamamasi
 * servis katmaninda (findScopedById) garanti edilir.
 *
 * <p>PARA KURALI: {@code birimFiyat} ve {@code toplamTutar} {@link BigDecimal} (NUMERIC(12,2)).
 * {@code adet} {@code int} (>0). Asla double/float kullanilmaz. {@code birimFiyat} satis aninda
 * urunun guncel {@code satisFiyati}'ndan KOPYALANIR; urun fiyati sonradan degisse bile DEGISMEZ.
 * {@code toplamTutar} = {@code birimFiyat} * {@code adet} (scale 2). Satista urun stogu ayni
 * transaction icinde dusurulur (serviste).
 *
 * <p>Satis DEGISMEZ ve SILINMEZ (immutable; PUT/DELETE yok).
 */
@Entity
@Table(name = "sale")
public class Sale extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urun_id", nullable = false)
    private Product urun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id")
    private Student ogrenci;

    @Column(name = "adet", nullable = false)
    private int adet;

    @Column(name = "birim_fiyat", precision = 12, scale = 2, nullable = false)
    private BigDecimal birimFiyat;

    @Column(name = "toplam_tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal toplamTutar;

    @Column(name = "satis_tarihi", nullable = false)
    private LocalDate satisTarihi;

    @Column(name = "aciklama")
    private String aciklama;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Sale() {
        // JPA icin
    }

    /** Bos satis ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Sale create() {
        return new Sale();
    }

    public Long getId() {
        return id;
    }

    public Product getUrun() {
        return urun;
    }

    public void setUrun(Product urun) {
        this.urun = urun;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public void setOgrenci(Student ogrenci) {
        this.ogrenci = ogrenci;
    }

    public int getAdet() {
        return adet;
    }

    public void setAdet(int adet) {
        this.adet = adet;
    }

    public BigDecimal getBirimFiyat() {
        return birimFiyat;
    }

    public void setBirimFiyat(BigDecimal birimFiyat) {
        this.birimFiyat = birimFiyat;
    }

    public BigDecimal getToplamTutar() {
        return toplamTutar;
    }

    public void setToplamTutar(BigDecimal toplamTutar) {
        this.toplamTutar = toplamTutar;
    }

    public LocalDate getSatisTarihi() {
        return satisTarihi;
    }

    public void setSatisTarihi(LocalDate satisTarihi) {
        this.satisTarihi = satisTarihi;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
