package com.artademi.schedule;

import com.artademi.common.tenant.TenantAware;
import com.artademi.group.Group;
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
import java.time.LocalTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Program (haftalık ders saati) is entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id}
 * ve global fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez (insert'te
 * @PrePersist TenantContext'ten set eder).
 *
 * <p>Bir kayit, bir gruba ({@code grup}, ZORUNLU) ait tek bir gun + saat araligini temsil eder.
 * {@code grup} @ManyToOne ile baglandigi icin yuklemede grubun tenant filtresi de uygulanir
 * (defense-in-depth) ve response'ta grup/salon/ogretmen ozeti erisilebilir. Grubun baska tenant'a
 * ait olamamasi servis katmaninda (findScopedById) garanti edilir.
 *
 * <p>Cakisma kurallari (ayni gun + saat ortusmesi) ve {@code bitisSaati > baslangicSaati} kurali
 * servis/validasyon katmaninda uygulanir (bkz. ScheduleService, @SaatAraligiGecerli).
 *
 * <p>Silme YOK: kayit silinmez, {@code aktif} alani ile pasiflestirilir (bkz. ScheduleService).
 */
@Entity
@Table(name = "schedule")
public class Schedule extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id", nullable = false)
    private Group grup;

    @Enumerated(EnumType.STRING)
    @Column(name = "gun", nullable = false, length = 20)
    private HaftaGunu gun;

    @Column(name = "baslangic_saati", nullable = false)
    private LocalTime baslangicSaati;

    @Column(name = "bitis_saati", nullable = false)
    private LocalTime bitisSaati;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Schedule() {
        // JPA icin
    }

    /** Bos program ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Schedule create() {
        return new Schedule();
    }

    public Long getId() {
        return id;
    }

    public Group getGrup() {
        return grup;
    }

    public void setGrup(Group grup) {
        this.grup = grup;
    }

    public HaftaGunu getGun() {
        return gun;
    }

    public void setGun(HaftaGunu gun) {
        this.gun = gun;
    }

    public LocalTime getBaslangicSaati() {
        return baslangicSaati;
    }

    public void setBaslangicSaati(LocalTime baslangicSaati) {
        this.baslangicSaati = baslangicSaati;
    }

    public LocalTime getBitisSaati() {
        return bitisSaati;
    }

    public void setBitisSaati(LocalTime bitisSaati) {
        this.bitisSaati = bitisSaati;
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
