package com.artademi.basvuru;

import com.artademi.branch.Branch;
import com.artademi.common.tenant.TenantAware;
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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Online on kayit basvurusu — kurumun paylastigi public baglantidan gelir.
 *
 * <p>{@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant
 * filtresine tabidir. ⚠️ Ancak bu kayit KIMLIKSIZ bir istekten olusur: tenant JWT'den degil,
 * URL'deki slug'dan cozulur ve {@code TenantContext}'e KISA SURELIGINE konur
 * (bkz. {@code PublicBasvuruController}). Basvuru, bu istisnaya sahip TEK entity'dir.
 *
 * <p>Silme YOK: basvuru silinmez, {@link BasvuruDurumu} ile takip edilir. Ogrenciye
 * donusturuldugunde {@link #ogrenci} dolar ve iz korunur.
 */
@Entity
@Table(name = "basvuru")
public class Basvuru extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 100)
    private String ad;

    @Column(name = "soyad", nullable = false, length = 100)
    private String soyad;

    @Column(name = "telefon", nullable = false, length = 30)
    private String telefon;

    @Column(name = "email", length = 255)
    private String email;

    /** Basvuran cocuksa velinin adi; yetiskin kendi basvuruyorsa bos kalir. */
    @Column(name = "veli_adi", length = 200)
    private String veliAdi;

    /** Ilgilenilen brans (opsiyonel — veli secmemis ya da kurum brans tanimlamamis olabilir). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brans_id")
    private Branch brans;

    @Column(name = "mesaj", length = 1000)
    private String mesaj;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private BasvuruDurumu durum = BasvuruDurumu.YENI;

    /** Donusturulduyse olusan ogrenci; mukerrer donusumu de engeller. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id")
    private Student ogrenci;

    /** Kotuye kullanim incelemesi icin kaynak IP. KVKK: kisisel veri sayilir. */
    @Column(name = "kaynak_ip", length = 45)
    private String kaynakIp;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Basvuru() {
        // JPA icin
    }

    public static Basvuru create() {
        return new Basvuru();
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

    public String getSoyad() {
        return soyad;
    }

    public void setSoyad(String soyad) {
        this.soyad = soyad;
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

    public String getVeliAdi() {
        return veliAdi;
    }

    public void setVeliAdi(String veliAdi) {
        this.veliAdi = veliAdi;
    }

    public Branch getBrans() {
        return brans;
    }

    public void setBrans(Branch brans) {
        this.brans = brans;
    }

    public String getMesaj() {
        return mesaj;
    }

    public void setMesaj(String mesaj) {
        this.mesaj = mesaj;
    }

    public BasvuruDurumu getDurum() {
        return durum;
    }

    public void setDurum(BasvuruDurumu durum) {
        this.durum = durum;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public void setOgrenci(Student ogrenci) {
        this.ogrenci = ogrenci;
    }

    public String getKaynakIp() {
        return kaynakIp;
    }

    public void setKaynakIp(String kaynakIp) {
        this.kaynakIp = kaynakIp;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
