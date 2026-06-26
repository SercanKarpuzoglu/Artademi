package com.artademi.group;

import com.artademi.branch.Branch;
import com.artademi.common.tenant.TenantAware;
import com.artademi.room.Room;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Grup/Sinif is entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id} ve global
 * fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez (insert'te
 * @PrePersist TenantContext'ten set eder).
 *
 * <p>Tablo adi {@code lesson_group} cunku "group" SQL'de rezerve kelimedir.
 *
 * <p>{@code brans} ve {@code ogretmen} ZORUNLU; {@code salon} GRUP'ta zorunlu, OZEL'de opsiyonel
 * (bu kosul DTO'daki {@code @GrupTutarli} ile uygulanir). Bu referanslar @ManyToOne ile baglandigi
 * icin yuklemede ilgili entity'lerin tenant filtresi de uygulanir (defense-in-depth) ve response'ta
 * ozet (ad) erisilebilir. Referanslarin baska tenant'a ait olamamasi servis katmaninda
 * (findScopedById) garanti edilir.
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. GroupService).
 */
@Entity
@Table(name = "lesson_group")
public class Group extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 20)
    private GrupTipi tip;

    /**
     * Model C: bu grubun hakedis tipi — grup tam olarak BIR tip ile odenir (cifte sayim imkansiz).
     * Ogretmenin ilgili {@code TeacherHakedis} satirindaki oran uygulanir. Istekte opsiyonel;
     * verilmezse grup tipinden varsayilan (GRUP->SAATLIK, OZEL->OZEL_DERS) atanir.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "hakedis_tipi", nullable = false, length = 20)
    private HakedisTipi hakedisTipi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brans_id", nullable = false)
    private Branch brans;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogretmen_id", nullable = false)
    private Teacher ogretmen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id")
    private Room salon;

    @Column(name = "seviye", length = 100)
    private String seviye;

    @Column(name = "aylik_aidat", precision = 10, scale = 2)
    private BigDecimal aylikAidat;

    @Column(name = "ders_basi_ucret", precision = 10, scale = 2)
    private BigDecimal dersBasiUcret;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Group() {
        // JPA icin
    }

    /** Bos grup ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Group create() {
        return new Group();
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

    public GrupTipi getTip() {
        return tip;
    }

    public void setTip(GrupTipi tip) {
        this.tip = tip;
    }

    public HakedisTipi getHakedisTipi() {
        return hakedisTipi;
    }

    public void setHakedisTipi(HakedisTipi hakedisTipi) {
        this.hakedisTipi = hakedisTipi;
    }

    public Branch getBrans() {
        return brans;
    }

    public void setBrans(Branch brans) {
        this.brans = brans;
    }

    public Teacher getOgretmen() {
        return ogretmen;
    }

    public void setOgretmen(Teacher ogretmen) {
        this.ogretmen = ogretmen;
    }

    public Room getSalon() {
        return salon;
    }

    public void setSalon(Room salon) {
        this.salon = salon;
    }

    public String getSeviye() {
        return seviye;
    }

    public void setSeviye(String seviye) {
        this.seviye = seviye;
    }

    public BigDecimal getAylikAidat() {
        return aylikAidat;
    }

    public void setAylikAidat(BigDecimal aylikAidat) {
        this.aylikAidat = aylikAidat;
    }

    public BigDecimal getDersBasiUcret() {
        return dersBasiUcret;
    }

    public void setDersBasiUcret(BigDecimal dersBasiUcret) {
        this.dersBasiUcret = dersBasiUcret;
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
}
