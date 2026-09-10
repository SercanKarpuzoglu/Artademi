package com.artademi.bildirim;

import com.artademi.common.tenant.TenantAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Kurumun otomatik bildirim tercihleri (kurum basina TEK satir).
 *
 * <p>⚠️ <b>Hepsi varsayilan KAPALI.</b> Borc hatirlatmasi bilincli olarak elle tetikleniyordu
 * ("otomatik borc takibi, okulun velisiyle iliskisini yonetmesini elinden alir"). Otomatiklestirme
 * bu karari iptal etmez, kurumun tercihine birakir: acmayan kurum elle akista kalir.
 */
@Entity
@Table(name = "bildirim_ayari")
public class BildirimAyari extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borc_hatirlatma_otomatik", nullable = false)
    private boolean borcHatirlatmaOtomatik = false;

    @Column(name = "devamsizlik_bildirimi", nullable = false)
    private boolean devamsizlikBildirimi = false;

    @Column(name = "haftalik_ozet", nullable = false)
    private boolean haftalikOzet = false;

    /** ISO-8601 gun numarasi: 1=Pazartesi … 7=Pazar. */
    @Column(name = "haftalik_ozet_gunu", nullable = false)
    private short haftalikOzetGunu = 1;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    @UpdateTimestamp
    @Column(name = "guncellenme_tarihi", nullable = false)
    private Instant guncellenmeTarihi;

    protected BildirimAyari() {
        // JPA icin
    }

    /** Kurumun henuz ayari yoksa kullanilan varsayilan (hepsi KAPALI). */
    public static BildirimAyari varsayilan() {
        return new BildirimAyari();
    }

    public Long getId() {
        return id;
    }

    public boolean isBorcHatirlatmaOtomatik() {
        return borcHatirlatmaOtomatik;
    }

    public void setBorcHatirlatmaOtomatik(boolean v) {
        this.borcHatirlatmaOtomatik = v;
    }

    public boolean isDevamsizlikBildirimi() {
        return devamsizlikBildirimi;
    }

    public void setDevamsizlikBildirimi(boolean v) {
        this.devamsizlikBildirimi = v;
    }

    public boolean isHaftalikOzet() {
        return haftalikOzet;
    }

    public void setHaftalikOzet(boolean v) {
        this.haftalikOzet = v;
    }

    public short getHaftalikOzetGunu() {
        return haftalikOzetGunu;
    }

    public void setHaftalikOzetGunu(short v) {
        this.haftalikOzetGunu = v;
    }

    /** Egitmene "bugun yoklama almadin" E-POSTASI (uygulama ici bildirim her zaman gider). */
    @Column(name = "yoklama_alinmadi_eposta", nullable = false)
    private boolean yoklamaAlinmadiEposta = false;

    public boolean isYoklamaAlinmadiEposta() {
        return yoklamaAlinmadiEposta;
    }

    public void setYoklamaAlinmadiEposta(boolean v) {
        this.yoklamaAlinmadiEposta = v;
    }
}
