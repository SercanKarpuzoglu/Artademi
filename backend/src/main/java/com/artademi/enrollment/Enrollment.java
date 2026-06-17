package com.artademi.enrollment;

import com.artademi.common.tenant.TenantAware;
import com.artademi.group.Group;
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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Kayit (enrollment) is entity'si — ogrenci ile grup arasindaki yazma iliskisi.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code ogrenci} ve {@code grup} ZORUNLU; @ManyToOne ile baglandiklari icin yuklemede ilgili
 * entity'lerin tenant filtresi de uygulanir (defense-in-depth) ve response'ta ozet (ad/soyad/tip)
 * erisilebilir. Referanslarin baska tenant'a ait olamamasi servis katmaninda (findScopedById)
 * garanti edilir.
 *
 * <p>Ucret/tahsilat YOK (sonraki modul). Silme YOK: ayrilma {@code durum=AYRILDI} +
 * {@code ayrilmaTarihi} ile yapilir (bkz. EnrollmentService).
 */
@Entity
@Table(name = "enrollment")
public class Enrollment extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id", nullable = false)
    private Group grup;

    @Column(name = "kayit_tarihi", nullable = false)
    private LocalDate kayitTarihi;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private EnrollmentDurumu durum = EnrollmentDurumu.AKTIF;

    @Column(name = "ayrilma_tarihi")
    private LocalDate ayrilmaTarihi;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Enrollment() {
        // JPA icin
    }

    /** Bos kayit ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Enrollment create() {
        return new Enrollment();
    }

    public Long getId() {
        return id;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public void setOgrenci(Student ogrenci) {
        this.ogrenci = ogrenci;
    }

    public Group getGrup() {
        return grup;
    }

    public void setGrup(Group grup) {
        this.grup = grup;
    }

    public LocalDate getKayitTarihi() {
        return kayitTarihi;
    }

    public void setKayitTarihi(LocalDate kayitTarihi) {
        this.kayitTarihi = kayitTarihi;
    }

    public EnrollmentDurumu getDurum() {
        return durum;
    }

    public void setDurum(EnrollmentDurumu durum) {
        this.durum = durum;
    }

    public LocalDate getAyrilmaTarihi() {
        return ayrilmaTarihi;
    }

    public void setAyrilmaTarihi(LocalDate ayrilmaTarihi) {
        this.ayrilmaTarihi = ayrilmaTarihi;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
