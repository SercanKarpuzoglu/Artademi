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

/**
 * Devamsizlik bildirimi izi — MUKERRER GONDERIM KALKANI.
 *
 * <p>Job her aksam calisir; ayni (ogrenci, oturum) icin ikinci kez mail gitmemelidir. Yoklama
 * sonradan duzeltilse veya job tekrar calissa bile veli iki kez uyarilmaz. Kilit veritabani
 * seviyesinde de vardir (V25 unique index) — uygulama kontrolu kacsa bile mukerrer kayit olusmaz.
 */
@Entity
@Table(name = "devamsizlik_bildirimi")
public class DevamsizlikBildirimi extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ogrenci_id", nullable = false)
    private Long ogrenciId;

    @Column(name = "oturum_id", nullable = false)
    private Long oturumId;

    @Column(name = "alici", nullable = false, length = 255)
    private String alici;

    @CreationTimestamp
    @Column(name = "olusturulma_tarihi", nullable = false, updatable = false)
    private Instant olusturulmaTarihi;

    protected DevamsizlikBildirimi() {
        // JPA icin
    }

    public static DevamsizlikBildirimi of(Long ogrenciId, Long oturumId, String alici) {
        DevamsizlikBildirimi d = new DevamsizlikBildirimi();
        d.ogrenciId = ogrenciId;
        d.oturumId = oturumId;
        d.alici = alici;
        return d;
    }

    public Long getId() {
        return id;
    }

    public Long getOgrenciId() {
        return ogrenciId;
    }

    public Long getOturumId() {
        return oturumId;
    }
}
