package com.artademi.payout;

import com.artademi.common.tenant.TenantAware;
import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;
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

/**
 * Hakedis (payout) is entity'si — bir ogretmenin belirli bir donemdeki kazanc kaydi.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code ogretmen} ZORUNLU; baska tenant'a ait olamamasi servis katmaninda (findScopedById)
 * garanti edilir. {@code hakedisTipi} hesaplama aninda ogretmenden KOPYALANIR.
 *
 * <p>PARA KURALI: tum parasal alanlar {@link BigDecimal} (NUMERIC(12,2)), oran alanlari NUMERIC(5,2),
 * scale 2 + RoundingMode.HALF_UP ile hesaplanir. Asla double/float kullanilmaz.
 *
 * <p>Hesaplama dokumu tipe gore farkli alanlarda tutulur:
 * <ul>
 *   <li>SAATLIK: {@code dersSayisi} (oturum sayisi) + {@code birimUcret} (saatlik ucret).</li>
 *   <li>CIRO_ORANI: {@code toplamTahsilat} + {@code kdvOrani} + {@code netCiro} + {@code oran}.</li>
 * </ul>
 *
 * <p>Ayni tenant'ta ayni ogretmen + donem icin tek hakediş olabilir (mukerrer engeli serviste 409 +
 * DB unique kisit ile).
 *
 * <p>Silme YOK.
 */
@Entity
@Table(name = "payout")
public class Payout extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogretmen_id", nullable = false)
    private Teacher ogretmen;

    @Column(name = "donem", length = 7, nullable = false)
    private String donem;

    @Enumerated(EnumType.STRING)
    @Column(name = "hakedis_tipi", length = 20, nullable = false)
    private HakedisTipi hakedisTipi;

    @Column(name = "hesaplanan_tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal hesaplananTutar;

    @Column(name = "ders_sayisi")
    private Integer dersSayisi;

    @Column(name = "birim_ucret", precision = 12, scale = 2)
    private BigDecimal birimUcret;

    @Column(name = "toplam_tahsilat", precision = 12, scale = 2)
    private BigDecimal toplamTahsilat;

    @Column(name = "kdv_orani", precision = 5, scale = 2)
    private BigDecimal kdvOrani;

    @Column(name = "net_ciro", precision = 12, scale = 2)
    private BigDecimal netCiro;

    @Column(name = "oran", precision = 5, scale = 2)
    private BigDecimal oran;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", length = 20, nullable = false)
    private PayoutDurumu durum = PayoutDurumu.HESAPLANDI;

    @Column(name = "odeme_tarihi")
    private LocalDate odemeTarihi;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Payout() {
        // JPA icin
    }

    /** Bos hakediş ornegi olusturur (servis kullanir; alanlar setter'larla doldurulur). */
    public static Payout create() {
        return new Payout();
    }

    public Long getId() {
        return id;
    }

    public Teacher getOgretmen() {
        return ogretmen;
    }

    public void setOgretmen(Teacher ogretmen) {
        this.ogretmen = ogretmen;
    }

    public String getDonem() {
        return donem;
    }

    public void setDonem(String donem) {
        this.donem = donem;
    }

    public HakedisTipi getHakedisTipi() {
        return hakedisTipi;
    }

    public void setHakedisTipi(HakedisTipi hakedisTipi) {
        this.hakedisTipi = hakedisTipi;
    }

    public BigDecimal getHesaplananTutar() {
        return hesaplananTutar;
    }

    public void setHesaplananTutar(BigDecimal hesaplananTutar) {
        this.hesaplananTutar = hesaplananTutar;
    }

    public Integer getDersSayisi() {
        return dersSayisi;
    }

    public void setDersSayisi(Integer dersSayisi) {
        this.dersSayisi = dersSayisi;
    }

    public BigDecimal getBirimUcret() {
        return birimUcret;
    }

    public void setBirimUcret(BigDecimal birimUcret) {
        this.birimUcret = birimUcret;
    }

    public BigDecimal getToplamTahsilat() {
        return toplamTahsilat;
    }

    public void setToplamTahsilat(BigDecimal toplamTahsilat) {
        this.toplamTahsilat = toplamTahsilat;
    }

    public BigDecimal getKdvOrani() {
        return kdvOrani;
    }

    public void setKdvOrani(BigDecimal kdvOrani) {
        this.kdvOrani = kdvOrani;
    }

    public BigDecimal getNetCiro() {
        return netCiro;
    }

    public void setNetCiro(BigDecimal netCiro) {
        this.netCiro = netCiro;
    }

    public BigDecimal getOran() {
        return oran;
    }

    public void setOran(BigDecimal oran) {
        this.oran = oran;
    }

    public PayoutDurumu getDurum() {
        return durum;
    }

    public void setDurum(PayoutDurumu durum) {
        this.durum = durum;
    }

    public LocalDate getOdemeTarihi() {
        return odemeTarihi;
    }

    public void setOdemeTarihi(LocalDate odemeTarihi) {
        this.odemeTarihi = odemeTarihi;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
