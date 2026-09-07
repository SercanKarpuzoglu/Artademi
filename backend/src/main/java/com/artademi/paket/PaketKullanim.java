package com.artademi.paket;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Tuketilen bir ders (bir kontor).
 *
 * <p>⚠️ Sayac yerine SATIR tutulur. Boylece yoklama duzeltmesi (GELDI → IZINLI) dogru
 * yansir: satir silinir, kalan ders geri gelir. Sayac tutulsaydi duzeltmede geri alma
 * adimi kacabilir ve sapma sessiz kalirdi.
 *
 * <p>⚠️ Benzersizlik anahtari (ogrenci, oturum) — paket DEGIL: ogrencinin iki paketi varsa
 * ayni dersten iki kontor dusmemelidir.
 */
@Entity
@Table(name = "paket_kullanim")
public class PaketKullanim extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paket_id", nullable = false)
    private Long paketId;

    @Column(name = "oturum_id", nullable = false)
    private Long oturumId;

    @Column(name = "ogrenci_id", nullable = false)
    private Long ogrenciId;

    @Column(name = "kullanim_tarihi", nullable = false)
    private LocalDate kullanimTarihi;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    protected PaketKullanim() {
        // JPA icin
    }

    static PaketKullanim of(Long paketId, Long oturumId, Long ogrenciId, LocalDate tarih) {
        PaketKullanim k = new PaketKullanim();
        k.paketId = paketId;
        k.oturumId = oturumId;
        k.ogrenciId = ogrenciId;
        k.kullanimTarihi = tarih;
        return k;
    }

    public Long getId() {
        return id;
    }

    public Long getPaketId() {
        return paketId;
    }

    public Long getOturumId() {
        return oturumId;
    }

    public Long getOgrenciId() {
        return ogrenciId;
    }

    public LocalDate getKullanimTarihi() {
        return kullanimTarihi;
    }
}
