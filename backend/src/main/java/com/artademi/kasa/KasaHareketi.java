package com.artademi.kasa;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tahsilat ve gider DISINDAKI kasa hareketi: transfer ve elle duzeltme.
 *
 * <p>⚠️ Tahsilat/gider buraya YAZILMAZ — onlar kendi tablolarinda durur ve kasaya
 * {@code kasa_id} ile baglidir. Buraya da yazilsalardi ayni para iki kez sayilirdi.
 *
 * <p>⚠️ Transfer IKI satirdir (kaynakta CIKIS, hedefte GIRIS), ortak {@link #transferGrubu}
 * ile baglidir. Tek satir olsaydi her bakiye sorgusu "bu satir bana giris mi cikis mi"
 * diye iki yone bakmak zorunda kalirdi.
 */
@Entity
@Table(name = "kasa_hareketi")
public class KasaHareketi extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kasa_id", nullable = false)
    private Kasa kasa;

    @Enumerated(EnumType.STRING)
    @Column(name = "yon", nullable = false, length = 10)
    private HareketYonu yon;

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", nullable = false, length = 20)
    private HareketTipi tip;

    /** HER ZAMAN pozitif; isaret {@link #yon} alanindadir. */
    @Column(name = "tutar", precision = 12, scale = 2, nullable = false)
    private BigDecimal tutar;

    @Column(name = "tarih", nullable = false)
    private LocalDate tarih;

    @Column(name = "aciklama", length = 500)
    private String aciklama;

    /** Transferin iki bacagini baglar; DUZELTME'de null. */
    @Column(name = "transfer_grubu")
    private UUID transferGrubu;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected KasaHareketi() {
        // JPA icin
    }

    static KasaHareketi of(Kasa kasa, HareketYonu yon, HareketTipi tip, BigDecimal tutar,
            LocalDate tarih, String aciklama, UUID transferGrubu) {
        KasaHareketi h = new KasaHareketi();
        h.kasa = kasa;
        h.yon = yon;
        h.tip = tip;
        h.tutar = tutar;
        h.tarih = tarih;
        h.aciklama = aciklama;
        h.transferGrubu = transferGrubu;
        return h;
    }

    public Long getId() {
        return id;
    }

    public Kasa getKasa() {
        return kasa;
    }

    public HareketYonu getYon() {
        return yon;
    }

    public HareketTipi getTip() {
        return tip;
    }

    public BigDecimal getTutar() {
        return tutar;
    }

    public LocalDate getTarih() {
        return tarih;
    }

    public String getAciklama() {
        return aciklama;
    }

    public UUID getTransferGrubu() {
        return transferGrubu;
    }

    public Instant getOlusturulmaTarihi() {
        return olusturulmaTarihi;
    }
}
