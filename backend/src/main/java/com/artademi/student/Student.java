package com.artademi.student;

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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Ogrenci is entity'si (2c-1). {@link TenantAware}'den turedigi icin {@code tenant_id}
 * ve global fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez
 * (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>Veli bilgisi ogrenci ICINDE tutulur (ayri tablo yok); iki veli olabilir (anne + baba).
 * Kardesler veli TC'si uzerinden eslesir.
 *
 * <p>Silme YOK: kayit silinmez, statu PASIF'e alinir (bkz. StudentService).
 */
@Entity
@Table(name = "students")
public class Student extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 100)
    private String ad;

    @Column(name = "soyad", nullable = false, length = 100)
    private String soyad;

    @Column(name = "tc_kimlik_no", nullable = false, length = 11)
    private String tcKimlikNo;

    @Column(name = "dogum_tarihi", nullable = false)
    private LocalDate dogumTarihi;

    @Column(name = "telefon", length = 20)
    private String telefon;

    @Column(name = "yetiskin_mi", nullable = false)
    private boolean yetiskinMi;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status;

    // --- Veli bilgisi (ogrenci icinde, hepsi opsiyonel) ---

    @Column(name = "anne_ad", length = 100)
    private String anneAd;

    @Column(name = "anne_tc_kimlik_no", length = 11)
    private String anneTcKimlikNo;

    @Column(name = "anne_telefon", length = 20)
    private String anneTelefon;

    @Column(name = "baba_ad", length = 100)
    private String babaAd;

    @Column(name = "baba_tc_kimlik_no", length = 11)
    private String babaTcKimlikNo;

    @Column(name = "baba_telefon", length = 20)
    private String babaTelefon;

    @Column(name = "veli_meslek", length = 150)
    private String veliMeslek;

    @Column(name = "ev_adresi", length = 500)
    private String evAdresi;

    @Column(name = "veli_mail", length = 255)
    private String veliMail;

    // --- Sistem alanlari ---

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Student() {
        // JPA icin
    }

    /** Bos ogrenci ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Student create() {
        return new Student();
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

    public String getTcKimlikNo() {
        return tcKimlikNo;
    }

    public void setTcKimlikNo(String tcKimlikNo) {
        this.tcKimlikNo = tcKimlikNo;
    }

    public LocalDate getDogumTarihi() {
        return dogumTarihi;
    }

    public void setDogumTarihi(LocalDate dogumTarihi) {
        this.dogumTarihi = dogumTarihi;
    }

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }

    public boolean isYetiskinMi() {
        return yetiskinMi;
    }

    public void setYetiskinMi(boolean yetiskinMi) {
        this.yetiskinMi = yetiskinMi;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public void setStatus(StudentStatus status) {
        this.status = status;
    }

    public String getAnneAd() {
        return anneAd;
    }

    public void setAnneAd(String anneAd) {
        this.anneAd = anneAd;
    }

    public String getAnneTcKimlikNo() {
        return anneTcKimlikNo;
    }

    public void setAnneTcKimlikNo(String anneTcKimlikNo) {
        this.anneTcKimlikNo = anneTcKimlikNo;
    }

    public String getAnneTelefon() {
        return anneTelefon;
    }

    public void setAnneTelefon(String anneTelefon) {
        this.anneTelefon = anneTelefon;
    }

    public String getBabaAd() {
        return babaAd;
    }

    public void setBabaAd(String babaAd) {
        this.babaAd = babaAd;
    }

    public String getBabaTcKimlikNo() {
        return babaTcKimlikNo;
    }

    public void setBabaTcKimlikNo(String babaTcKimlikNo) {
        this.babaTcKimlikNo = babaTcKimlikNo;
    }

    public String getBabaTelefon() {
        return babaTelefon;
    }

    public void setBabaTelefon(String babaTelefon) {
        this.babaTelefon = babaTelefon;
    }

    public String getVeliMeslek() {
        return veliMeslek;
    }

    public void setVeliMeslek(String veliMeslek) {
        this.veliMeslek = veliMeslek;
    }

    public String getEvAdresi() {
        return evAdresi;
    }

    public void setEvAdresi(String evAdresi) {
        this.evAdresi = evAdresi;
    }

    public String getVeliMail() {
        return veliMail;
    }

    public void setVeliMail(String veliMail) {
        this.veliMail = veliMail;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
