package com.artademi.teacher;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Ogretmen is entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id} ve global
 * fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez (insert'te
 * @PrePersist TenantContext'ten set eder).
 *
 * <p>Ogretmen <-> Brans cok-coga iliskisi ACIK baglanti entity'si {@link TeacherBranch}
 * uzerinden tutulur (join satiri da tenant_id tasisin diye). Brans atamasi {@link #setBranchLinks}
 * ile yonetilir (mevcutlar temizlenip yenileri eklenir).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. TeacherService).
 */
@Entity
@Table(name = "teachers")
public class Teacher extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 100)
    private String ad;

    @Column(name = "soyad", nullable = false, length = 100)
    private String soyad;

    @Column(name = "telefon", length = 20)
    private String telefon;

    @Column(name = "email", length = 255)
    private String email;

    /** Keycloak kullanici id'si — opsiyonel; ilgili kullanici olusturulunca elle dolar. */
    @Column(name = "keycloak_user_id", length = 255)
    private String keycloakUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hakedis_tipi", nullable = false, length = 20)
    private HakedisTipi hakedisTipi;

    @Column(name = "saatlik_ucret", precision = 10, scale = 2)
    private BigDecimal saatlikUcret;

    @Column(name = "ciro_orani", precision = 5, scale = 2)
    private BigDecimal ciroOrani;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    /**
     * Ogretmene atanmis branslar (acik baglanti entity'si uzerinden). cascade=ALL +
     * orphanRemoval sayesinde {@link #setBranchLinks} ile yonetilir; her TeacherBranch
     * kendi tenant_id'sini @PrePersist'te alir.
     */
    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TeacherBranch> branchLinks = new HashSet<>();

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Teacher() {
        // JPA icin
    }

    /** Bos ogretmen ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Teacher create() {
        return new Teacher();
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

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public HakedisTipi getHakedisTipi() {
        return hakedisTipi;
    }

    public void setHakedisTipi(HakedisTipi hakedisTipi) {
        this.hakedisTipi = hakedisTipi;
    }

    public BigDecimal getSaatlikUcret() {
        return saatlikUcret;
    }

    public void setSaatlikUcret(BigDecimal saatlikUcret) {
        this.saatlikUcret = saatlikUcret;
    }

    public BigDecimal getCiroOrani() {
        return ciroOrani;
    }

    public void setCiroOrani(BigDecimal ciroOrani) {
        this.ciroOrani = ciroOrani;
    }

    public boolean isAktif() {
        return aktif;
    }

    public void setAktif(boolean aktif) {
        this.aktif = aktif;
    }

    public Set<TeacherBranch> getBranchLinks() {
        return branchLinks;
    }

    /**
     * Brans atamasini topluca yeniden kurar: mevcut baglantilari temizler (orphanRemoval ile
     * silinir), verilen branch'ler icin yeni {@link TeacherBranch} baglantilari ekler.
     * Branch'ler servis katmaninda tenant-guvenli ({@code findScopedById}) cozulur.
     */
    public void setBranchLinks(Iterable<TeacherBranch> links) {
        this.branchLinks.clear();
        for (TeacherBranch link : links) {
            link.setTeacher(this);
            this.branchLinks.add(link);
        }
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
