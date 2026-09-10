package com.artademi.paket;

import com.artademi.common.tenant.TenantAware;
import com.artademi.finance.Accrual;
import com.artademi.group.Group;
import com.artademi.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.Filter;
import com.artademi.common.silme.SoftDeletable;

/**
 * Ogrenciye satilan ders paketi (kontor).
 *
 * <p>Ucuncu fiyatlandirma modeli: mevcut ikisi grup uzerindeydi ({@code aylikAidat},
 * {@code dersBasiUcret}); paket OGRENCI uzerindedir.
 *
 * <p>⚠️ Kalan ders SAKLANMAZ — {@code toplamDers} eksi kullanim satiri sayisi olarak
 * hesaplanir. Sayac tutulsaydi yoklama duzeltmelerinde (GELDI → IZINLI) sapar ve sapma
 * sessiz olurdu.
 */
@Entity
@Table(name = "ders_paketi")
@Filter(name = SoftDeletable.FILTRE)
public class DersPaketi extends TenantAware implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    /** Pakete bagli grup (opsiyonel); kontor once bu grubun derslerinden duser. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id")
    private Group grup;

    @Column(name = "toplam_ders", nullable = false)
    private int toplamDers;

    @Column(name = "tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal tutar;

    @Column(name = "satis_tarihi", nullable = false)
    private LocalDate satisTarihi;

    /** NULL = suresiz. */
    @Column(name = "son_kullanma_tarihi")
    private LocalDate sonKullanmaTarihi;

    /** Satista uretilen PESIN tahakkuk. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accrual_id")
    private Accrual accrual;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private PaketDurumu durum = PaketDurumu.AKTIF;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected DersPaketi() {
        // JPA icin
    }

    static DersPaketi of(Student ogrenci, String ad, Group grup, int toplamDers,
            BigDecimal tutar, LocalDate satisTarihi, LocalDate sonKullanma, String aciklama) {
        DersPaketi p = new DersPaketi();
        p.ogrenci = ogrenci;
        p.ad = ad;
        p.grup = grup;
        p.toplamDers = toplamDers;
        p.tutar = tutar;
        p.satisTarihi = satisTarihi;
        p.sonKullanmaTarihi = sonKullanma;
        p.aciklama = aciklama;
        return p;
    }

    /** Suresi dolmus mu? HESAPLANIR, saklanmaz. */
    public boolean suresiDolduMu(LocalDate bugun) {
        return sonKullanmaTarihi != null && sonKullanmaTarihi.isBefore(bugun);
    }

    /** Kontor dusumu icin uygun mu: aktif ve suresi dolmamis. */
    public boolean kullanilabilirMi(LocalDate bugun) {
        return durum == PaketDurumu.AKTIF && !suresiDolduMu(bugun);
    }

    void iptalEt() {
        this.durum = PaketDurumu.IPTAL;
    }

    void accrualBagla(Accrual accrual) {
        this.accrual = accrual;
    }

    public Long getId() {
        return id;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public String getAd() {
        return ad;
    }

    public Group getGrup() {
        return grup;
    }

    public int getToplamDers() {
        return toplamDers;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public LocalDate getSatisTarihi() {
        return satisTarihi;
    }

    public LocalDate getSonKullanmaTarihi() {
        return sonKullanmaTarihi;
    }

    public Accrual getAccrual() {
        return accrual;
    }

    public PaketDurumu getDurum() {
        return durum;
    }

    public String getAciklama() {
        return aciklama;
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
