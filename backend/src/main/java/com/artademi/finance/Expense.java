package com.artademi.finance;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Gider (expense) is entity'si — kurumun gideri (ogrenci/gruba bagli DEGIL).
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code kategori} serbest metin (opsiyonel). PARA KURALI: {@code tutar} {@link BigDecimal}
 * (NUMERIC(12,2)), pozitif olmalidir (DTO @Positive). Asla double/float kullanilmaz.
 *
 * <p>Silme YOK.
 */
@Entity
@Table(name = "expense")
public class Expense extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal tutar;

    @Column(name = "gider_tarihi", nullable = false)
    private LocalDate giderTarihi;

    @Column(name = "kategori", length = 100)
    private String kategori;

    @Column(name = "aciklama")
    private String aciklama;

    /** Giderin cikildigi kasa. NULLABLE — bkz. Payment.kasa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kasa_id")
    private com.artademi.kasa.Kasa kasa;

    /** Gider kime yapildi. NULLABLE — tedarikci tanimlamak zorunlu degildir. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tedarikci_id")
    private com.artademi.tedarikci.Tedarikci tedarikci;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Expense() {
        // JPA icin
    }

    /** Bos gider ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Expense create() {
        return new Expense();
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public void setTutar(BigDecimal tutar) {
        this.tutar = tutar;
    }

    public LocalDate getGiderTarihi() {
        return giderTarihi;
    }

    public void setGiderTarihi(LocalDate giderTarihi) {
        this.giderTarihi = giderTarihi;
    }

    public String getKategori() {
        return kategori;
    }

    public void setKategori(String kategori) {
        this.kategori = kategori;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public com.artademi.kasa.Kasa getKasa() {
        return kasa;
    }

    public void setKasa(com.artademi.kasa.Kasa kasa) {
        this.kasa = kasa;
    }

    public com.artademi.tedarikci.Tedarikci getTedarikci() {
        return tedarikci;
    }

    public void setTedarikci(com.artademi.tedarikci.Tedarikci tedarikci) {
        this.tedarikci = tedarikci;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
