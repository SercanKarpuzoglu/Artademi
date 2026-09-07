package com.artademi.tedarikci;

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
 * Tedarikci (kiralik salon sahibi, kirtasiye, temizlik firmasi…).
 *
 * <p>Giderler tedarikciye baglanabilir; boylece "bu ay kime ne kadar odedik" sorusu
 * cevaplanabilir.
 *
 * <p>⚠️ Bu bir CARI HESAP DEGILDIR: fatura/borc-alacak takibi yoktur, yalnizca giderlerin
 * kime yapildigi tutulur. Cari hesap, fatura ve odeme kalemlerini ayri ayri modellemeyi
 * gerektirir; simdilik kapsam disi.
 *
 * <p>Silme YOK: {@code aktif} ile pasiflestirilir — gecmis giderler bagli kalir.
 */
@Entity
@Table(name = "tedarikci")
public class Tedarikci extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 200)
    private String ad;

    @Column(name = "telefon", length = 30)
    private String telefon;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "vergi_no", length = 20)
    private String vergiNo;

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

    protected Tedarikci() {
        // JPA icin
    }

    public static Tedarikci create() {
        return new Tedarikci();
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

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVergiNo() {
        return vergiNo;
    }

    public void setVergiNo(String vergiNo) {
        this.vergiNo = vergiNo;
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
}
