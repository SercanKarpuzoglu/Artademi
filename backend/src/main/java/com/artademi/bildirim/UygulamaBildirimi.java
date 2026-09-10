package com.artademi.bildirim;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uygulama ici bildirim (V33). Hedef: roller (virgulle) ve/veya tek kullanici (Keycloak sub).
 * Okunma satir bazinda (kucuk kurum). Silinmez; zil son 30'u gosterir.
 */
@Entity
@Table(name = "uygulama_bildirimi")
public class UygulamaBildirimi extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 40)
    private UygulamaBildirimTipi tip;

    @Column(name = "hedef_roller", nullable = false, length = 120)
    private String hedefRoller;

    @Column(name = "hedef_kullanici", length = 120)
    private String hedefKullanici;

    @Column(name = "baslik", nullable = false, length = 200)
    private String baslik;

    @Column(name = "metin")
    private String metin;

    @Column(name = "baglanti", length = 200)
    private String baglanti;

    @Column(name = "okundu_tarihi")
    private Instant okunduTarihi;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    protected UygulamaBildirimi() {
        // JPA icin
    }

    public static UygulamaBildirimi of(UygulamaBildirimTipi tip, String hedefRoller, String hedefKullanici,
            String baslik, String metin, String baglanti) {
        UygulamaBildirimi b = new UygulamaBildirimi();
        b.tip = tip;
        b.hedefRoller = hedefRoller;
        b.hedefKullanici = hedefKullanici;
        b.baslik = baslik;
        b.metin = metin;
        b.baglanti = baglanti;
        return b;
    }

    public Long getId() {
        return id;
    }

    public UygulamaBildirimTipi getTip() {
        return tip;
    }

    public String getHedefRoller() {
        return hedefRoller;
    }

    public String getHedefKullanici() {
        return hedefKullanici;
    }

    public String getBaslik() {
        return baslik;
    }

    public String getMetin() {
        return metin;
    }

    public String getBaglanti() {
        return baglanti;
    }

    public Instant getOkunduTarihi() {
        return okunduTarihi;
    }

    public void setOkunduTarihi(Instant okunduTarihi) {
        this.okunduTarihi = okunduTarihi;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }
}
