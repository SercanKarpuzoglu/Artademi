package com.artademi.finance;

import com.artademi.common.tenant.TenantAware;
import com.artademi.group.Group;
import com.artademi.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Tahakkuk (accrual) is entity'si — bir ogrenciye kesilen borc/ucret kalemi.
 * {@link TenantAware}'den turedigi icin {@code tenant_id} ve global fail-closed tenant filtresine
 * otomatik tabidir; tenant_id ELLE yonetilmez (insert'te @PrePersist TenantContext'ten set eder).
 *
 * <p>{@code ogrenci} ZORUNLU; {@code grup} ve {@code donem} OPSIYONEL. Referanslarin baska tenant'a
 * ait olamamasi servis katmaninda (findScopedById) garanti edilir.
 *
 * <p>PARA KURALI: {@code tutar} {@link BigDecimal} (NUMERIC(12,2)), pozitif olmalidir (DTO @Positive).
 * Asla double/float kullanilmaz.
 *
 * <p>Silme YOK.
 */
@Entity
@Table(name = "accrual")
public class Accrual extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id")
    private Group grup;

    @Column(name = "donem", length = 7)
    private String donem;

    @Column(name = "tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal tutar;

    @Column(name = "aciklama")
    private String aciklama;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected Accrual() {
        // JPA icin
    }

    /** Bos tahakkuk ornegi olusturur (mapper kullanir; alanlar setter'larla doldurulur). */
    public static Accrual create() {
        return new Accrual();
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

    public String getDonem() {
        return donem;
    }

    public void setDonem(String donem) {
        this.donem = donem;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public void setTutar(BigDecimal tutar) {
        this.tutar = tutar;
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
