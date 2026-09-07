package com.artademi.telafi;

import com.artademi.attendance.AttendanceSession;
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
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Ogrencinin telafi ders hakki.
 *
 * <p>⚠️ Hak OTOMATIK DOGMAZ; kurum verir. Her devamsizliktan otomatik uretilseydi liste
 * kullanilamaz hale gelir ve kurumun kendi kurali ("haber verdiyse telafi veririm") ezilirdi.
 *
 * <p>Silme YOK: hak {@link TelafiDurumu#IPTAL} ile geri alinir; kimin ne zaman hak kazandigi
 * ve kullandigi izi korunur.
 */
@Entity
@Table(name = "telafi_hakki")
public class TelafiHakki extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    /** Hakkin dogdugu devamsizlik; kurum devamsizliga bagli olmadan da hak tanimlayabilir. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kaynak_oturum_id")
    private AttendanceSession kaynakOturum;

    @Column(name = "verilme_tarihi", nullable = false)
    private LocalDate verilmeTarihi;

    /** NULL = suresiz. */
    @Column(name = "son_kullanma_tarihi")
    private LocalDate sonKullanmaTarihi;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private TelafiDurumu durum = TelafiDurumu.BEKLIYOR;

    /** Hak hangi derste kullanildi (kanit). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kullanilan_oturum_id")
    private AttendanceSession kullanilanOturum;

    @Column(name = "kullanim_tarihi")
    private LocalDate kullanimTarihi;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected TelafiHakki() {
        // JPA icin
    }

    static TelafiHakki of(Student ogrenci, AttendanceSession kaynak, LocalDate verilme,
            LocalDate sonKullanma, String aciklama) {
        TelafiHakki t = new TelafiHakki();
        t.ogrenci = ogrenci;
        t.kaynakOturum = kaynak;
        t.verilmeTarihi = verilme;
        t.sonKullanmaTarihi = sonKullanma;
        t.aciklama = aciklama;
        return t;
    }

    /**
     * Suresi dolmus mu? HESAPLANIR, saklanmaz.
     *
     * <p>Yalnizca BEKLIYOR haklar icin anlamlidir: kullanilmis bir hak sonradan
     * "suresi doldu" olmaz.
     */
    public boolean suresiDolduMu(LocalDate bugun) {
        return durum == TelafiDurumu.BEKLIYOR
                && sonKullanmaTarihi != null
                && sonKullanmaTarihi.isBefore(bugun);
    }

    void kullan(AttendanceSession oturum, LocalDate tarih) {
        this.durum = TelafiDurumu.KULLANILDI;
        this.kullanilanOturum = oturum;
        this.kullanimTarihi = tarih;
    }

    void iptalEt() {
        this.durum = TelafiDurumu.IPTAL;
    }

    public Long getId() {
        return id;
    }

    public Student getOgrenci() {
        return ogrenci;
    }

    public AttendanceSession getKaynakOturum() {
        return kaynakOturum;
    }

    public LocalDate getVerilmeTarihi() {
        return verilmeTarihi;
    }

    public LocalDate getSonKullanmaTarihi() {
        return sonKullanmaTarihi;
    }

    public TelafiDurumu getDurum() {
        return durum;
    }

    public AttendanceSession getKullanilanOturum() {
        return kullanilanOturum;
    }

    public LocalDate getKullanimTarihi() {
        return kullanimTarihi;
    }

    public String getAciklama() {
        return aciklama;
    }
}
