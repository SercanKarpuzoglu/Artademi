package com.artademi.attendance;

import com.artademi.common.tenant.TenantAware;
import com.artademi.group.Group;
import com.artademi.schedule.Schedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Yoklama oturumu is entity'si. {@link TenantAware}'den turedigi icin {@code tenant_id} ve global
 * fail-closed tenant filtresine otomatik tabidir; tenant_id ELLE yonetilmez (insert'te
 * @PrePersist TenantContext'ten set eder).
 *
 * <p>Bir oturum, bir grubun ({@code grup}, ZORUNLU) belirli bir tarihteki ({@code tarih}, ZORUNLU)
 * yoklamasini temsil eder. Oturumun var olmasi = yoklama alinmis demektir; ayri bir statu alani
 * YOKTUR. {@code program} (haftalik ders saati) opsiyoneldir; verilirse oturumun hangi programa ait
 * oldugunu baglar.
 *
 * <p><b>NOT:</b> alan/kolon adi {@code notu}; "not" hem Java anahtar kelimesi hem SQL rezerve
 * kelimesi oldugundan her yerde (entity, DTO, JSON, kolon) {@code notu} kullanilir.
 *
 * <p>Referanslar @ManyToOne ile baglandigi icin yuklemede ilgili entity'lerin tenant filtresi de
 * uygulanir (defense-in-depth). Referanslarin baska tenant'a ait olamamasi servis katmaninda
 * (findScopedById) garanti edilir.
 *
 * <p>Ayni tenant'ta ayni grup + tarih icin tek oturum olabilir (mukerrer engeli serviste 409 +
 * DB unique kisit ile).
 */
@Entity
@Table(name = "attendance_session")
public class AttendanceSession extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id", nullable = false)
    private Group grup;

    @Column(name = "tarih", nullable = false)
    private LocalDate tarih;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Schedule program;

    @Column(name = "notu")
    private String notu;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected AttendanceSession() {
        // JPA icin
    }

    /** Bos oturum ornegi olusturur (servis kullanir; alanlar setter'larla doldurulur). */
    public static AttendanceSession create() {
        return new AttendanceSession();
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

    public LocalDate getTarih() {
        return tarih;
    }

    public void setTarih(LocalDate tarih) {
        this.tarih = tarih;
    }

    public Schedule getProgram() {
        return program;
    }

    public void setProgram(Schedule program) {
        this.program = program;
    }

    public String getNotu() {
        return notu;
    }

    public void setNotu(String notu) {
        this.notu = notu;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
