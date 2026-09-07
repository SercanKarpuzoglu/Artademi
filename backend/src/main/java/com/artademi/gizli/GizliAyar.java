package com.artademi.gizli;

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
 * Kurumun sifreli saklanan bir ayari (SMS/WhatsApp gibi entegrasyon kimlik bilgileri).
 *
 * <p>⚠️ {@link #degerSifreli} DISARI VERILMEZ ve LOGLANMAZ. Arayuz yalnizca {@link #maske}
 * gorur ("••••4821") — yonetici dogru anahtari girip girmedigini bundan anlar. Duz deger
 * yalnizca gonderim aninda, sunucu icinde cozulur.
 *
 * <p>{@link TenantAware}'den turedigi icin global fail-closed tenant filtresine tabidir:
 * bir kurumun kimlik bilgisi baska kurumun sorgusunda GORUNMEZ.
 */
@Entity
@Table(name = "gizli_ayar")
public class GizliAyar extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anahtar", nullable = false, length = 100)
    private String anahtar;

    @Column(name = "deger_sifreli", nullable = false, columnDefinition = "text")
    private String degerSifreli;

    @Column(name = "maske", length = 8)
    private String maske;

    @Column(name = "guncelleyen", length = 150)
    private String guncelleyen;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected GizliAyar() {
        // JPA icin
    }

    static GizliAyar of(String anahtar, String degerSifreli, String maske, String guncelleyen) {
        GizliAyar g = new GizliAyar();
        g.anahtar = anahtar;
        g.degerSifreli = degerSifreli;
        g.maske = maske;
        g.guncelleyen = guncelleyen;
        return g;
    }

    public Long getId() {
        return id;
    }

    public String getAnahtar() {
        return anahtar;
    }

    /** ⚠️ Sifreli hâli. Cozmek icin {@link Sifreleme#coz}; sonucu ASLA disari verme. */
    String getDegerSifreli() {
        return degerSifreli;
    }

    public String getMaske() {
        return maske;
    }

    public String getGuncelleyen() {
        return guncelleyen;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }

    void guncelle(String degerSifreli, String maske, String guncelleyen) {
        this.degerSifreli = degerSifreli;
        this.maske = maske;
        this.guncelleyen = guncelleyen;
    }
}
