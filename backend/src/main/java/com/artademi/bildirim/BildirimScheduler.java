package com.artademi.bildirim;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.reminder.BorcHatirlatmaService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Otomatik bildirim job'lari.
 *
 * <h2>⚠️ Zamanlanmis is + multi-tenant</h2>
 * Zamanlanmis is bir ISTEKTEN dogmaz; dolayisiyla {@code TenantContext} BOSTUR ve fail-closed
 * filtre yuzunden tum TenantAware sorgular BOS doner. Bu yuzden is, kurumlari tek tek dolasip
 * baglami KENDISI kurar. Bu, "tenant yalnizca JWT'den okunur" kuralinin ikinci bilincli
 * istisnasidir; guvenli kilan sinirlar:
 * <ul>
 *   <li>Tenant listesi PLATFORM tablosundan gelir (istemci girdisi yok, sahtelenemez).</li>
 *   <li>Yalnizca AKTIF kurumlar islenir — askidaki kurumun velisine mail gitmez.</li>
 *   <li>Baglam her kurumdan sonra {@code finally} ile TEMIZLENIR; sizarsa bir sonraki
 *       kurumun isi yanlis tenant'ta calisirdi.</li>
 *   <li>Bir kurumun hatasi digerlerini DURDURMAZ (tek tek try/catch).</li>
 * </ul>
 *
 * <p>Test'te scheduler'a guvenilmez; servis metodlari dogrudan cagrilarak deterministik
 * test edilir (bkz. SubscriptionScheduler ile ayni yaklasim).
 */
@Component
public class BildirimScheduler {

    private static final Logger log = LoggerFactory.getLogger(BildirimScheduler.class);

    /**
     * ⚠️ Cron'lar ve tarih hesabi TURKIYE saatine gore.
     *
     * <p>Konteynerde {@code TZ} ayarli DEGILDIR, JVM UTC calisir. {@code zone} verilmezse
     * "aksam 20:00" aslinda 23:00'te (TR) tetiklenir ve veliye gece yarisi mail gider —
     * bildirimin amaci tam da bunun tersi. Ayni sekilde {@code LocalDate.now()} da UTC
     * gunu verir; gun donumune yakin saatlerde YANLIS gune bakar.
     */
    static final java.time.ZoneId TURKIYE = java.time.ZoneId.of("Europe/Istanbul");

    private final TenantRepository tenants;
    private final BildirimAyariService ayarlar;
    private final OtomatikBildirimService bildirimler;
    private final BorcHatirlatmaService borcHatirlatma;

    public BildirimScheduler(TenantRepository tenants, BildirimAyariService ayarlar,
            OtomatikBildirimService bildirimler, BorcHatirlatmaService borcHatirlatma) {
        this.tenants = tenants;
        this.ayarlar = ayarlar;
        this.bildirimler = bildirimler;
        this.borcHatirlatma = borcHatirlatma;
    }

    /**
     * Her aksam 20:00 — O GUNUN devamsizliklari.
     *
     * <p>Aksam calisir cunku veli "bugun gelmedi" bilgisini AYNI GUN ister; ertesi sabah
     * gonderilen bir devamsizlik bildirimi ise yaramaz. Saat TURKIYE saatidir
     * ({@code zone}) — JVM UTC oldugu icin bu belirtilmezse 23:00'te gonderilirdi.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Europe/Istanbul")
    public void devamsizlikJobu() {
        LocalDate bugun = LocalDate.now(TURKIYE);
        log.info("Devamsızlık bildirimi işi başlıyor: {}", bugun);
        int toplam = kurumlariDolas(tenant -> {
            if (!ayarlar.aktifAyar().isDevamsizlikBildirimi()) {
                return;
            }
            int n = bildirimler.devamsizlikBildirimleri(bugun);
            if (n > 0) {
                log.info("Devamsızlık bildirimi gönderildi (tenant={}, adet={})",
                        tenant.getId(), n);
            }
        });
        log.info("Devamsızlık bildirimi işi bitti: {} kurum işlendi", toplam);
    }

    /**
     * Her sabah 04:30 — borc hatirlatmasi ve (gunu geldiyse) haftalik ozet.
     *
     * <p>04:30 (TR), abonelik job'indan SONRA: o is askiya alma/odeme durumlarini gunceller;
     * bildirimler guncel durum uzerinden gitmelidir. NOT: {@code SubscriptionScheduler}
     * zone BELIRTMEZ, yani UTC 03:00 = TR 06:00'da calisir — yine de bu isten oncedir,
     * dolayisiyla sira korunur.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Istanbul")
    public void sabahJobu() {
        LocalDate bugun = LocalDate.now(TURKIYE);
        int bugunIso = bugun.getDayOfWeek().getValue(); // 1=Pazartesi … 7=Pazar
        log.info("Sabah bildirim işi başlıyor: {} (ISO gün {})", bugun, bugunIso);

        int toplam = kurumlariDolas(tenant -> {
            BildirimAyari ayar = ayarlar.aktifAyar();

            if (ayar.isBorcHatirlatmaOtomatik()) {
                int n = borcHatirlatma.otomatikGonder();
                if (n > 0) {
                    log.info("Otomatik borç hatırlatması gönderildi (tenant={}, adet={})",
                            tenant.getId(), n);
                }
            }

            if (ayar.isHaftalikOzet() && ayar.getHaftalikOzetGunu() == bugunIso) {
                if (bildirimler.haftalikOzet()) {
                    log.info("Haftalık özet gönderildi (tenant={})", tenant.getId());
                }
            }
        });
        log.info("Sabah bildirim işi bitti: {} kurum işlendi", toplam);
    }

    /**
     * Her aksam 21:00 — o gunun yoklamasi alinmamis dersleri (Dalga C). Devamsizlik isinden (20:00)
     * sonra: egitmen 20:00'e kadar almamissa ofis de haberdar olsun. Uygulama ici bildirim her zaman;
     * e-posta kurum tercihiyle.
     */
    @Scheduled(cron = "0 0 21 * * *", zone = "Europe/Istanbul")
    public void yoklamaAlinmadiJobu() {
        LocalDate bugun = LocalDate.now(TURKIYE);
        int toplam = kurumlariDolas(tenant -> {
            int n = bildirimler.yoklamaAlinmadi(bugun, ayarlar.aktifAyar().isYoklamaAlinmadiEposta());
            if (n > 0) {
                log.info("Yoklama alınmadı bildirimi (tenant={}, ders={})", tenant.getId(), n);
            }
        });
        log.info("Yoklama alınmadı işi bitti: {} kurum işlendi", toplam);
    }

    /**
     * AKTIF kurumlari dolasir; her biri icin {@link TenantContext}'i kurar, isi calistirir ve
     * baglami TEMIZLER.
     *
     * <p>Bir kurumun hatasi digerlerini durdurmaz: aksi halde tek bozuk kurum tum
     * platformun bildirimlerini susturabilirdi.
     *
     * @return islenen kurum sayisi
     */
    private int kurumlariDolas(Consumer<Tenant> is) {
        List<Tenant> aktifler = tenants.findByStatus(TenantStatus.AKTIF);
        int islenen = 0;
        for (Tenant tenant : aktifler) {
            UUID onceki = TenantContext.get();
            TenantContext.set(tenant.getId());
            try {
                is.accept(tenant);
                islenen++;
            } catch (RuntimeException e) {
                log.error("Bildirim işi başarısız (tenant={}): {}", tenant.getId(),
                        e.getMessage());
            } finally {
                // ⚠️ Sizarsa bir sonraki kurumun isi YANLIS tenant'ta calisir.
                if (onceki == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.set(onceki);
                }
            }
        }
        return islenen;
    }

    /** Test/operasyon icin elle tetikleme (cron beklemeden). */
    void devamsizlikJobuCalistir(LocalDate gun) {
        kurumlariDolas(tenant -> {
            if (ayarlar.aktifAyar().isDevamsizlikBildirimi()) {
                bildirimler.devamsizlikBildirimleri(gun);
            }
        });
    }

    /** Test/operasyon icin elle tetikleme (cron beklemeden). */
    void sabahJobuCalistir(DayOfWeek gun) {
        int iso = gun.getValue();
        kurumlariDolas(tenant -> {
            BildirimAyari ayar = ayarlar.aktifAyar();
            if (ayar.isBorcHatirlatmaOtomatik()) {
                borcHatirlatma.otomatikGonder();
            }
            if (ayar.isHaftalikOzet() && ayar.getHaftalikOzetGunu() == iso) {
                bildirimler.haftalikOzet();
            }
        });
    }
}
