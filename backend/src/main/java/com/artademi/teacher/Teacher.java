package com.artademi.teacher;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * <p>Model C: hakedis tipi ogretmenin uzerinde TEK alan DEGIL; ogretmen birden cok hakedis tipi
 * TANIMLAYABILIR ({@link TeacherHakedis} satirlari, oranlari tasir). Hangi tipin uygulanacagini
 * GRUP belirler (bkz. {@code Group.hakedisTipi}). Hakedis listesi {@link #setHakedisler} ile
 * yonetilir (reconcile — branchLinks ile birebir ayni delta mantigi).
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

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    /**
     * Ogretmene atanmis branslar (acik baglanti entity'si uzerinden). cascade=ALL +
     * orphanRemoval sayesinde {@link #setBranchLinks} ile yonetilir; her TeacherBranch
     * kendi tenant_id'sini @PrePersist'te alir.
     */
    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TeacherBranch> branchLinks = new HashSet<>();

    /**
     * Ogretmenin TANIMLADIGI hakedis tipleri + oranlari (Model C). cascade=ALL + orphanRemoval
     * sayesinde {@link #setHakedisler} ile yonetilir; her {@link TeacherHakedis} kendi
     * tenant_id'sini @PrePersist'te alir. Her tip yalnizca BIR kez (DB UNIQUE).
     */
    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TeacherHakedis> hakedisler = new HashSet<>();

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
     * Brans atamasini istenen kumeyle UZLASTIRIR (reconcile): yalnizca artik istenmeyen baglantilar
     * silinir (orphanRemoval), yalnizca YENI branslar icin {@link TeacherBranch} eklenir; zaten bagli
     * olanlar OLDUGU GIBI birakilir. Branch'ler servis katmaninda tenant-guvenli ({@code findScopedById})
     * cozulur.
     *
     * <p>Neden clear()+addAll DEGIL: ayni brans yeniden gonderildiginde clear() eski satiri silmeye,
     * add() ayni (teacher_id, branch_id) ile yeni satir eklemeye calisirdi; Hibernate insert'i
     * delete'ten ONCE flush ettiginden {@code uq_teacher_branch} unique kisiti ihlal edilir (500).
     * Uzlastirma bu cakismayi onler ve guncellemeyi idempotent kilar.
     */
    public void setBranchLinks(Iterable<TeacherBranch> links) {
        Set<Long> istenenBransIds = new LinkedHashSet<>();
        for (TeacherBranch link : links) {
            if (link.getBranch() != null) {
                istenenBransIds.add(link.getBranch().getId());
            }
        }
        // Artik istenmeyen baglantilari kaldir (orphanRemoval ile silinir).
        this.branchLinks.removeIf(existing -> existing.getBranch() == null
                || !istenenBransIds.contains(existing.getBranch().getId()));
        // Halihazirda bagli brans id'leri.
        Set<Long> mevcutBransIds = new HashSet<>();
        for (TeacherBranch existing : this.branchLinks) {
            if (existing.getBranch() != null) {
                mevcutBransIds.add(existing.getBranch().getId());
            }
        }
        // Yalnizca YENI branslar icin baglanti ekle (mevcutlar tekrar olusturulmaz -> insert cakismasi yok).
        for (TeacherBranch link : links) {
            if (link.getBranch() != null && mevcutBransIds.add(link.getBranch().getId())) {
                link.setTeacher(this);
                this.branchLinks.add(link);
            }
        }
    }

    public Set<TeacherHakedis> getHakedisler() {
        return hakedisler;
    }

    /**
     * Hakedis listesini istenen kumeyle UZLASTIRIR (reconcile) — {@link #setBranchLinks} ile birebir
     * AYNI delta mantigi (tip bazinda): artik istenmeyen tipler silinir (orphanRemoval), yalnizca
     * YENI tipler icin satir eklenir; zaten var olan tipler OLDUGU GIBI birakilir.
     *
     * <p>Var olan bir tip yeniden gonderildiyse, tutar(lar)i mevcut satir uzerinde GUNCELLENIR
     * (silip-yeniden-ekleme YOK) — boylece {@code uq (teacher_id, tip)} insert-before-delete 500
     * hatasi olusmaz ve guncelleme idempotent kalir (bkz. setBranchLinks yorumu).
     */
    public void setHakedisler(Iterable<TeacherHakedis> rows) {
        Set<HakedisTipi> istenenTipler = new LinkedHashSet<>();
        for (TeacherHakedis row : rows) {
            if (row.getTip() != null) {
                istenenTipler.add(row.getTip());
            }
        }
        // Artik istenmeyen tipleri kaldir (orphanRemoval ile silinir).
        this.hakedisler.removeIf(existing -> existing.getTip() == null
                || !istenenTipler.contains(existing.getTip()));
        // Halihazirda var olan tipleri tutar guncellemesi icin haritala.
        for (TeacherHakedis row : rows) {
            if (row.getTip() == null) {
                continue;
            }
            TeacherHakedis mevcut = null;
            for (TeacherHakedis existing : this.hakedisler) {
                if (existing.getTip() == row.getTip()) {
                    mevcut = existing;
                    break;
                }
            }
            if (mevcut != null) {
                // Var olan tip: tutarlari guncelle (silme/yeniden ekleme YOK -> uq cakismasi yok).
                mevcut.setSaatlikUcret(row.getSaatlikUcret());
                mevcut.setCiroOrani(row.getCiroOrani());
                mevcut.setDersBasiUcret(row.getDersBasiUcret());
            } else {
                // Yeni tip: ekle.
                row.setTeacher(this);
                this.hakedisler.add(row);
            }
        }
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
