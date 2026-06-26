package com.artademi.teacher;

import com.artademi.common.tenant.TenantAware;
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

/**
 * Ogretmenin TANIMLADIGI bir hakedis tipi + ona ait oran (Model C). ACIK baglanti entity'si:
 * bir ogretmen birden cok hakedis tipi tasiyabilir (her tip icin tek satir).
 *
 * <p>{@link TenantAware}'den turedigi icin her satir da {@code tenant_id} tasir ve global tenant
 * filtresine tabidir (yazma sirasinda @PrePersist TenantContext'ten set eder) — tipki
 * {@link TeacherBranch} gibi.
 *
 * <p>Hangi tutar alaninin dolu oldugu {@code tip}'e baglidir; yalnizca tip ile eslesen alan dolar:
 * <ul>
 *   <li>SAATLIK -> {@code saatlikUcret} (&gt; 0).</li>
 *   <li>CIRO_ORANI -> {@code ciroOrani} (0 &lt; oran &le; 100).</li>
 *   <li>OZEL_DERS -> {@code dersBasiUcret} (&gt; 0).</li>
 * </ul>
 * Bu kosullu zorunluluk {@code @HakedisTutarli} (liste duzeyi) validasyonu ile saglanir.
 *
 * <p>Ayni ogretmen icin ayni tip yalnizca BIR kez (DB UNIQUE (teacher_id, tip)). Reconcile mantigi
 * ({@link Teacher#setHakedisler}) mevcut satirlari korur, yalnizca delta ekler/siler — boylece
 * uq insert-before-delete 500 hatasi olusmaz (bkz. TeacherBranch reconcile yorumu).
 */
@Entity
@Table(name = "teacher_hakedis")
public class TeacherHakedis extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 20)
    private HakedisTipi tip;

    @Column(name = "saatlik_ucret", precision = 10, scale = 2)
    private BigDecimal saatlikUcret;

    @Column(name = "ciro_orani", precision = 5, scale = 2)
    private BigDecimal ciroOrani;

    @Column(name = "ders_basi_ucret", precision = 10, scale = 2)
    private BigDecimal dersBasiUcret;

    protected TeacherHakedis() {
        // JPA icin
    }

    /**
     * Verilen tip + tutarlar icin yeni hakedis satiri olusturur (teacher, Teacher.setHakedisler'de
     * baglanir). Yalnizca tip ile eslesen tutar alani anlamlidir; digerleri null beklenir.
     */
    public static TeacherHakedis of(HakedisTipi tip, BigDecimal saatlikUcret, BigDecimal ciroOrani,
            BigDecimal dersBasiUcret) {
        TeacherHakedis h = new TeacherHakedis();
        h.tip = tip;
        h.saatlikUcret = saatlikUcret;
        h.ciroOrani = ciroOrani;
        h.dersBasiUcret = dersBasiUcret;
        return h;
    }

    public Long getId() {
        return id;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public HakedisTipi getTip() {
        return tip;
    }

    public void setTip(HakedisTipi tip) {
        this.tip = tip;
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

    public BigDecimal getDersBasiUcret() {
        return dersBasiUcret;
    }

    public void setDersBasiUcret(BigDecimal dersBasiUcret) {
        this.dersBasiUcret = dersBasiUcret;
    }
}
