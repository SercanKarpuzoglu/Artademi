package com.artademi.basvuru;

import com.artademi.basvuru.dto.BasvuruFormBilgisi;
import com.artademi.basvuru.dto.BasvuruGonderRequest;
import com.artademi.branch.Branch;
import com.artademi.branch.BranchRepository;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public on kayit formu — KIMLIKSIZ ({@code /api/public/basvuru/**}).
 *
 * <h2>⚠️ Tenant kuralinin bilincli istisnasi</h2>
 * Projenin demir kurali "tenant YALNIZCA JWT'den okunur"dur. Burada JWT YOKTUR: tenant,
 * URL'deki <b>slug</b>'dan cozulur. Bu istisnayi guvenli kilan sinirlar:
 * <ol>
 *   <li><b>Slug yetki tasimaz.</b> Yalnizca "hangi kurumun formu" sorusunu yanitlar; hicbir
 *       okuma/yazma hakki vermez. Kurum bu baglantiyi zaten kamuya duyurur.</li>
 *   <li><b>Yuzey iki uctan ibarettir:</b> form bilgisi okuma ve basvuru yazma. Baska hicbir
 *       is verisi bu yoldan okunamaz.</li>
 *   <li><b>Yalnizca AKTIF kurum.</b> ASKIDA/SILINDI kurumun formu 404 doner — odemesi
 *       duran kurum altyapimiz uzerinden talep toplamaya devam edemez.</li>
 *   <li><b>Baglam dar kapsamlidir.</b> {@link TenantContext} yalnizca islem suresince set
 *       edilir ve {@code finally}'de ONCEKI degerine geri alinir.</li>
 * </ol>
 *
 * <p>Kotuye kullanim korumasi: honeypot + IP basina soguma + ayni telefonla mukerrer
 * gonderim engeli (bkz. {@link #gonder}).
 */
@Service
public class PublicBasvuruService {

    private static final Logger log = LoggerFactory.getLogger(PublicBasvuruService.class);

    /** Ayni IP, AYNI FORMA bu sure icinde ikinci basvuru gonderemez. */
    static final long SOGUMA_SANIYE = 60;

    /** Soguma haritasi bu boyutu asinca suresi dolmus kayitlar budanir (sinirsiz buyume olmasin). */
    private static final int SOGUMA_BUDAMA_ESIGI = 10_000;

    /** Ayni telefon bu sure icinde tekrar basvurursa yeni kayit ACILMAZ (mukerrer engeli). */
    static final Duration MUKERRER_PENCERE = Duration.ofHours(24);

    private final TenantRepository tenants;
    private final BasvuruRepository basvurular;
    private final BranchRepository branslar;
    private final BasvuruBildirimService bildirim;

    /**
     * (slug + IP) → son gonderim zamani; naif bellek-ici soguma (tek instance icin yeterli).
     *
     * <p>⚠️ Anahtar YALNIZCA IP DEGILDIR: ortak IP arkasindaki (NAT, ofis, site) iki farkli
     * veli — hele farkli kurumlara basvururken — birbirini engellememeli. Sadece IP ile
     * anahtarlamak gercek bir talebi kaybettirir. Ayni forma hizli tekrar gonderim ise
     * hala engellenir (bot korumasi bundan ibarettir; anlamli mukerrer kontrolu
     * telefon uzerinden yapilir).
     */
    private final Map<String, Instant> sonGonderim = new ConcurrentHashMap<>();

    public PublicBasvuruService(TenantRepository tenants, BasvuruRepository basvurular,
            BranchRepository branslar, BasvuruBildirimService bildirim) {
        this.tenants = tenants;
        this.basvurular = basvurular;
        this.branslar = branslar;
        this.bildirim = bildirim;
    }

    /** Formun acilis bilgisi (kurum adi + aktif branslar). Slug yoksa/kurum aktif degilse 404. */
    @Transactional(readOnly = true)
    public BasvuruFormBilgisi formBilgisi(String slug) {
        Tenant tenant = aktifTenantCoz(slug);
        return tenantBaglamindaCalistir(tenant.getId(), () -> {
            var secenekler = branslar.findByAktifTrueOrderByAdAsc().stream()
                    .map(b -> new BasvuruFormBilgisi.BransSecenegi(b.getId(), b.getAd()))
                    .toList();
            return new BasvuruFormBilgisi(tenant.getAd(), secenekler);
        });
    }

    /**
     * Basvuruyu kaydeder.
     *
     * <p>Honeypot doluysa kayit ACILMAZ ama cagirana basari donulur — bot, engellendigini
     * anlamasin. Ayni telefon 24 saat icinde tekrar gonderirse de yeni kayit acilmaz
     * (veli formu iki kez gondermis olabilir); bu da basari gibi doner ki kullanici
     * "gitmedi mi?" diye tekrar tekrar denemesin.
     */
    @Transactional
    public void gonder(String slug, BasvuruGonderRequest istek, String ip) {
        Tenant tenant = aktifTenantCoz(slug);

        if (istek.botMu()) {
            log.info("Başvuru honeypot yakaladı (slug={}, ip={}) — yok sayıldı", slug, ip);
            return;
        }
        soguma(slug, ip);

        tenantBaglamindaCalistir(tenant.getId(), () -> {
            String telefon = istek.telefon().trim();
            if (!basvurular.findRecentByTelefon(telefon,
                    Instant.now().minus(MUKERRER_PENCERE)).isEmpty()) {
                log.info("Mükerrer başvuru atlandı (slug={}, telefon gizli)", slug);
                return null;
            }

            Basvuru b = Basvuru.create();
            b.setAd(istek.ad().trim());
            b.setSoyad(istek.soyad().trim());
            b.setTelefon(telefon);
            b.setEmail(bosaNull(istek.email()));
            b.setVeliAdi(bosaNull(istek.veliAdi()));
            b.setMesaj(bosaNull(istek.mesaj()));
            b.setKaynakIp(ip);
            b.setBrans(bransCoz(istek.bransId()));
            basvurular.save(b);

            // Bildirim gonderimi basvuruyu KAYBETTIRMEMELI: mail patlarsa kayit yine durur.
            bildirim.yeniBasvuru(tenant, b);
            return null;
        });
    }

    /**
     * Brans secildiyse AYNI tenant'a ait oldugunu dogrular.
     *
     * <p>Baglam zaten o tenant'a set edilmis durumda oldugundan {@code findScopedById}
     * yabanci brans icin bos doner — istemci baska kurumun brans id'sini gondererek
     * capraz-tenant referans olusturamaz.
     */
    private Branch bransCoz(Long bransId) {
        if (bransId == null) {
            return null;
        }
        return branslar.findScopedById(bransId)
                .orElseThrow(() -> new NotFoundException("Branş bulunamadı: " + bransId));
    }

    /** Slug'i AKTIF bir tenant'a cozer; yoksa/aktif degilse 404 (kurum varligini sizdirmadan). */
    private Tenant aktifTenantCoz(String slug) {
        return tenants.findByBasvuruSlug(slug)
                .filter(t -> t.getStatus() == TenantStatus.AKTIF)
                .orElseThrow(() -> new NotFoundException("Başvuru formu bulunamadı"));
    }

    /**
     * Verilen tenant baglaminda calistirir ve baglami ONCEKI degerine geri alir.
     *
     * <p>Blanket {@code clear()} YAPILMAZ: istek kimlikli de gelmis olabilir (ornegin giris
     * yapmis bir kullanici public baglantiyi acarsa), o zaman onun baglamini silmek yanlis
     * olurdu. Thread havuzda yeniden kullanildigi icin geri alma {@code finally}'dedir.
     */
    private <T> T tenantBaglamindaCalistir(UUID tenantId, Supplier<T> is) {
        UUID onceki = TenantContext.get();
        TenantContext.set(tenantId);
        try {
            return is.get();
        } finally {
            if (onceki == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(onceki);
            }
        }
    }

    private void soguma(String slug, String ip) {
        Instant simdi = Instant.now();
        if (sonGonderim.size() > SOGUMA_BUDAMA_ESIGI) {
            sonGonderim.values().removeIf(t -> t.plusSeconds(SOGUMA_SANIYE).isBefore(simdi));
        }
        Instant son = sonGonderim.put(slug + "|" + ip, simdi);
        if (son != null && son.plusSeconds(SOGUMA_SANIYE).isAfter(simdi)) {
            throw new ConflictException(
                    "Çok sık gönderim. Lütfen bir dakika sonra tekrar deneyin.");
        }
    }

    private static String bosaNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
