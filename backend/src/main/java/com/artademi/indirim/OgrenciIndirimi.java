package com.artademi.indirim;

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
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Indirimin OGRENCIYE OZEL atamasi (V34): grup NULL = tum gruplar; [baslangic, bitis] araligi (bitis NULL =
 * surekli). Tahakkuk uretimi donemin ilk gununu bu araliga gore test eder.
 */
@Entity
@Table(name = "ogrenci_indirimi")
public class OgrenciIndirimi extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ogrenci_id", nullable = false)
    private Student ogrenci;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indirim_id", nullable = false)
    private IndirimTanimi indirim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grup_id")
    private Group grup;

    @Column(name = "baslangic", nullable = false)
    private LocalDate baslangic;

    @Column(name = "bitis")
    private LocalDate bitis;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected OgrenciIndirimi() {
        // JPA icin
    }

    public static OgrenciIndirimi create() {
        return new OgrenciIndirimi();
    }

    /** Verilen tarihte (donem basi) gecerli mi: aktif + tanim aktif + tarih araligi + grup eslesmesi. */
    public boolean gecerli(LocalDate tarih, Long grupId) {
        if (!aktif || indirim == null || !indirim.isAktif()) {
            return false;
        }
        if (tarih.isBefore(baslangic) || (bitis != null && tarih.isAfter(bitis))) {
            return false;
        }
        return grup == null || (grupId != null && grup.getId().equals(grupId));
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

    public IndirimTanimi getIndirim() {
        return indirim;
    }

    public void setIndirim(IndirimTanimi indirim) {
        this.indirim = indirim;
    }

    public Group getGrup() {
        return grup;
    }

    public void setGrup(Group grup) {
        this.grup = grup;
    }

    public LocalDate getBaslangic() {
        return baslangic;
    }

    public void setBaslangic(LocalDate baslangic) {
        this.baslangic = baslangic;
    }

    public LocalDate getBitis() {
        return bitis;
    }

    public void setBitis(LocalDate bitis) {
        this.bitis = bitis;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
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
}
