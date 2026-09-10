package com.artademi.indirim;

import com.artademi.common.silme.SoftDeletable;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/** Indirim / kampanya tanimi (V34). Kurum bazli; ogrenciye {@link OgrenciIndirimi} ile atanir. */
@Entity
@Table(name = "indirim_tanimi")
@Filter(name = SoftDeletable.FILTRE)
public class IndirimTanimi extends TenantAware implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 120)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 10)
    private IndirimTipi tip;

    @Column(name = "deger", precision = 12, scale = 2, nullable = false)
    private BigDecimal deger;

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

    @Column(name = "silindi_tarihi")
    private Instant silindiTarihi;

    @Column(name = "silen", length = 100)
    private String silen;

    protected IndirimTanimi() {
        // JPA icin
    }

    public static IndirimTanimi create() {
        return new IndirimTanimi();
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

    public IndirimTipi getTip() {
        return tip;
    }

    public void setTip(IndirimTipi tip) {
        this.tip = tip;
    }

    public BigDecimal getDeger() {
        return deger;
    }

    public void setDeger(BigDecimal deger) {
        this.deger = deger;
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

    @Override
    public Instant getSilindiTarihi() {
        return silindiTarihi;
    }

    @Override
    public void setSilindiTarihi(Instant silindiTarihi) {
        this.silindiTarihi = silindiTarihi;
    }

    @Override
    public void setSilen(String silen) {
        this.silen = silen;
    }

    /** Ekranda kisa etiket: "%15" / "500 ₺". */
    public String etiket() {
        String d = deger.stripTrailingZeros().toPlainString();
        return tip == IndirimTipi.ORAN ? "%" + d : d + " ₺";
    }
}
