package com.artademi.donem;

import com.artademi.common.silme.SoftDeletable;
import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/** Egitim donemi (V35): "2026-27 Guz" 14 Eyl - 31 Oca. Grup buna baglanir; donemlik kayit bunun sonunda biter. */
@Entity
@Table(name = "donem")
@Filter(name = SoftDeletable.FILTRE)
public class Donem extends TenantAware implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 120)
    private String ad;

    @Column(name = "baslangic", nullable = false)
    private LocalDate baslangic;

    @Column(name = "bitis", nullable = false)
    private LocalDate bitis;

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

    protected Donem() {
        // JPA icin
    }

    public static Donem create() {
        return new Donem();
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

    public LocalDate getBaslangic() {
        return baslangic;
    }

    public void setBaslangic(LocalDate baslangic) {
        this.baslangic = baslangic;
    }

    public LocalDate getBitis() {
        return bitis;
    }

    public void setBitis(LocalDate bitis) {
        this.bitis = bitis;
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
}
