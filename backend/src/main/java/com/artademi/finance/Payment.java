package com.artademi.finance;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tahsilat (payment) is entity'si — bir ogrenciden alinan odeme.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code ogrenci} ZORUNLU; {@code accrual} (tahakkuk) ve {@code grup} OPSIYONEL. accrual verilirse
 * o tahakkugun ogrencisi ile payment ogrencisi AYNI olmalidir (servis kontrolu -> 400). Referanslarin
 * baska tenant'a ait olamamasi servis katmaninda (findScopedById) garanti edilir.
 *
 * <p>PARA KURALI: {@code tutar} {@link BigDecimal} (NUMERIC(12,2)), pozitif olmalidir (DTO @Positive).
 * Asla double/float kullanilmaz.
 *
 * <p>Silme YOK.
 */
@Entity
@Table(name = "payment")
public class Payment extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accrual_id")
    private Accrual accrual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id")
    private Group grup;

    @Column(name = "tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal tutar;

    @Column(name = "odeme_tarihi", nullable = false)
    private LocalDate odemeTarihi;

    @Enumerated(EnumType.STRING)
    @Column(name = "odeme_yontemi", length = 20, nullable = false)
    private OdemeYontemi odemeYontemi;

    @Column(name = "aciklama")
    private String aciklama;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Payment() {
        // JPA icin
    }

    /** Bos tahsilat ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Payment create() {
        return new Payment();
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

    public Accrual getAccrual() {
        return accrual;
    }

    public void setAccrual(Accrual accrual) {
        this.accrual = accrual;
    }

    public Group getGrup() {
        return grup;
    }

    public void setGrup(Group grup) {
        this.grup = grup;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public void setTutar(BigDecimal tutar) {
        this.tutar = tutar;
    }

    public LocalDate getOdemeTarihi() {
        return odemeTarihi;
    }

    public void setOdemeTarihi(LocalDate odemeTarihi) {
        this.odemeTarihi = odemeTarihi;
    }

    public OdemeYontemi getOdemeYontemi() {
        return odemeYontemi;
    }

    public void setOdemeYontemi(OdemeYontemi odemeYontemi) {
        this.odemeYontemi = odemeYontemi;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }

    public Instant getGuncellenmeTarihi() {
        return guncellenmeTarihi;
    }
}
