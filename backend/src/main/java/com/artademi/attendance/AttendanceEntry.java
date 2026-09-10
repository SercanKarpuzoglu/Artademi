package com.artademi.attendance;

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
import org.hibernate.annotations.Filter;
import com.artademi.common.silme.SoftDeletable;

/**
 * Yoklama girisi is entity'si — bir oturumdaki tek bir ogrencinin durumu. {@link TenantAware}'den
 * turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine otomatik tabidir;
 * tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code session} ve {@code ogrenci} ZORUNLU; @ManyToOne ile baglandiklari icin yuklemede ilgili
 * entity'lerin tenant filtresi de uygulanir (defense-in-depth). {@code durum} ZORUNLU
 * (GELDI/GELMEDI/IZINLI); oturum olusturulurken GELMEDI varsayilani ile uretilir.
 *
 * <p>Bir oturumda bir ogrenci icin tek giris olabilir (DB unique kisit ile).
 */
@Entity
@Table(name = "attendance_entry")
@Filter(name = SoftDeletable.FILTRE)
public class AttendanceEntry extends TenantAware implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private YoklamaDurumu durum;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected AttendanceEntry() {
        // JPA icin
    }

    /** Bos giris ornegi olusturur (servis kullanir; alanlar setter'larla doldurulur). */
    public static AttendanceEntry create() {
        return new AttendanceEntry();
    }

    public Long getId() {
        return id;
    }

    public AttendanceSession getSession() {
        return session;
    }

    public void setSession(AttendanceSession session) {
        this.session = session;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public void setOgrenci(Student ogrenci) {
        this.ogrenci = ogrenci;
    }

    public YoklamaDurumu getDurum() {
        return durum;
    }

    public void setDurum(YoklamaDurumu durum) {
        this.durum = durum;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }

    // --- Yumusak silme (V32): bkz. SoftDeletable. Sorgularda @SQLRestriction ile gizlenir. ---
    @Column(name = "silindi_tarihi")
    private Instant silindiTarihi;

    @Column(name = "silen", length = 100)
    private String silen;

    @Override
    public Instant getSilindiTarihi() {
        return silindiTarihi;
    }

    @Override
    public void setSilindiTarihi(Instant silindiTarihi) {
        this.silindiTarihi = silindiTarihi;
    }

    public String getSilen() {
        return silen;
    }

    @Override
    public void setSilen(String silen) {
        this.silen = silen;
    }
}
