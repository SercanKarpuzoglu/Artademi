package com.artademi.platform;

import com.artademi.billing.BillingEvent;
import com.artademi.billing.BillingEventRepository;
import com.artademi.billing.BillingProperties;
import com.artademi.platform.dto.PlatformDashboardResponse;
import com.artademi.platform.dto.PlatformDashboardResponse.DikkatSatiri;
import com.artademi.platform.dto.PlatformDashboardResponse.Gelir;
import com.artademi.platform.dto.PlatformDashboardResponse.HareketSatiri;
import com.artademi.platform.dto.PlatformDashboardResponse.KurumSayilari;
import com.artademi.platform.dto.PlatformDashboardResponse.YenilemeSatiri;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform konsolu genel bakis verisi (SADECE SUPER_ADMIN uzerinden cagrilir).
 *
 * <p>⚠️ Buradaki tutarlar PLATFORM GELIRIDIR (okullarin bize odedigi), okulun kendi tahsilati
 * DEGIL — subscription-billing skill'indeki ayrim. Karistirilirsa raporlar anlamsizlasir.
 *
 * <p>Tenant/Subscription tenant-filtresine tabi olmadigi icin duz {@code findAll} dogrudur
 * (findScopedById kuralinin platform istisnasi).
 */
@Service
public class PlatformDashboardService {

    /** Yaklasan yenileme penceresi (gun). */
    private static final int YENILEME_PENCERESI = 7;
    /** Genel bakista gosterilen son hareket sayisi. */
    private static final int SON_HAREKET_SAYISI = 10;

    private final TenantRepository tenants;
    private final SubscriptionRepository subscriptions;
    private final BillingEventRepository events;
    private final BillingProperties billingProps;

    public PlatformDashboardService(TenantRepository tenants, SubscriptionRepository subscriptions,
            BillingEventRepository events, BillingProperties billingProps) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.events = events;
        this.billingProps = billingProps;
    }

    @Transactional(readOnly = true)
    public PlatformDashboardResponse build(LocalDate today) {
        List<Tenant> hepsi = tenants.findAll();
        Map<UUID, Tenant> tenantById = hepsi.stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        List<Subscription> abonelikler = subscriptions.findAll();

        return new PlatformDashboardResponse(
                kurumSayilari(hepsi, today),
                abonelikSayilari(abonelikler),
                gelir(abonelikler, tenantById),
                dikkatGerektirenler(abonelikler, tenantById),
                yaklasanYenilemeler(abonelikler, tenantById, today),
                sonHareketler(tenantById));
    }

    private KurumSayilari kurumSayilari(List<Tenant> hepsi, LocalDate today) {
        Map<TenantStatus, Long> statuBazinda = new EnumMap<>(TenantStatus.class);
        for (TenantStatus s : TenantStatus.values()) {
            statuBazinda.put(s, 0L);
        }
        long buAyYeni = 0;
        LocalDate ayBasi = today.withDayOfMonth(1);
        for (Tenant t : hepsi) {
            statuBazinda.merge(t.getStatus(), 1L, Long::sum);
            LocalDate olusturma = t.getCreatedAt() == null ? null
                    : t.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (olusturma != null && !olusturma.isBefore(ayBasi)) {
                buAyYeni++;
            }
        }
        // "Toplam"da SILINDI sayilmaz: soft-delete edilmis kurum isletme sayisi degildir.
        long toplam = hepsi.size() - statuBazinda.getOrDefault(TenantStatus.SILINDI, 0L);
        return new KurumSayilari(toplam, buAyYeni, statuBazinda);
    }

    private Map<SubscriptionStatus, Long> abonelikSayilari(List<Subscription> abonelikler) {
        Map<SubscriptionStatus, Long> sayilar = new EnumMap<>(SubscriptionStatus.class);
        for (SubscriptionStatus s : SubscriptionStatus.values()) {
            sayilar.put(s, 0L);
        }
        abonelikler.forEach(s -> sayilar.merge(s.getStatus(), 1L, Long::sum));
        return sayilar;
    }

    /**
     * MRR = odeyen kurum × aylik plan ucreti. "Odeyen" = abonelik AKTIF + plan AYLIK + odeme ODENDI.
     * DENEME'dekiler ve grace'tekiler SAYILMAZ (henuz para gelmedi) — gelir abartilmaz.
     */
    private Gelir gelir(List<Subscription> abonelikler, Map<UUID, Tenant> tenantById) {
        long odeyen = abonelikler.stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.AKTIF)
                .filter(s -> s.getPlan() == Plan.AYLIK)
                .filter(s -> s.getPaymentStatus() == PaymentStatus.ODENDI)
                // Silinmis kurumun aboneligi gelire sayilmaz.
                .filter(s -> {
                    Tenant t = tenantById.get(s.getTenantId());
                    return t != null && t.getStatus() != TenantStatus.SILINDI;
                })
                .count();
        BigDecimal ucret = billingProps.aylikPlanUcreti();
        return new Gelir(odeyen, ucret.multiply(BigDecimal.valueOf(odeyen)), ucret);
    }

    /** Aksiyon bekleyenler: grace'te olan, odemesi basarisiz olan ve askidaki kurumlar. */
    private List<DikkatSatiri> dikkatGerektirenler(List<Subscription> abonelikler,
            Map<UUID, Tenant> tenantById) {
        List<DikkatSatiri> satirlar = new ArrayList<>();
        for (Subscription s : abonelikler) {
            Tenant t = tenantById.get(s.getTenantId());
            if (t == null || t.getStatus() == TenantStatus.SILINDI) {
                continue;
            }
            String sebep = null;
            if (s.getStatus() == SubscriptionStatus.ODEME_BEKLIYOR) {
                sebep = "Ödeme bekleniyor (erişim açık)";
            } else if (s.getPaymentStatus() == PaymentStatus.BASARISIZ) {
                sebep = "Son tahsilat başarısız";
            } else if (s.getStatus() == SubscriptionStatus.ASKIDA
                    || t.getStatus() == TenantStatus.ASKIDA) {
                sebep = "Askıda — erişim kapalı";
            }
            if (sebep != null) {
                satirlar.add(new DikkatSatiri(t.getId(), t.getAd(), t.getStatus(), s.getStatus(),
                        s.getPaymentStatus(), s.getGraceEndsAt(), sebep));
            }
        }
        satirlar.sort(Comparator.comparing(DikkatSatiri::ad, String.CASE_INSENSITIVE_ORDER));
        return satirlar;
    }

    private List<YenilemeSatiri> yaklasanYenilemeler(List<Subscription> abonelikler,
            Map<UUID, Tenant> tenantById, LocalDate today) {
        LocalDate sinir = today.plusDays(YENILEME_PENCERESI);
        return abonelikler.stream()
                .filter(s -> s.getCurrentPeriodEnd() != null)
                .filter(s -> !s.getCurrentPeriodEnd().isBefore(today)
                        && !s.getCurrentPeriodEnd().isAfter(sinir))
                .filter(s -> {
                    Tenant t = tenantById.get(s.getTenantId());
                    return t != null && t.getStatus() != TenantStatus.SILINDI;
                })
                .sorted(Comparator.comparing(Subscription::getCurrentPeriodEnd))
                .map(s -> new YenilemeSatiri(
                        s.getTenantId(),
                        tenantById.get(s.getTenantId()).getAd(),
                        s.getCurrentPeriodEnd(),
                        s.getProviderSubscriptionRef() != null))
                .toList();
    }

    private List<HareketSatiri> sonHareketler(Map<UUID, Tenant> tenantById) {
        return events.findAll(PageRequest.of(0, SON_HAREKET_SAYISI,
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(e -> new HareketSatiri(
                        e.getEventType(),
                        e.getStatus().name(),
                        kurumAdi(tenantById, e),
                        e.getCreatedAt()))
                .getContent();
    }

    private static String kurumAdi(Map<UUID, Tenant> tenantById, BillingEvent e) {
        if (e.getTenantId() == null) {
            return "—"; // eslesmeyen bildirim (IGNORED): kurum bilinmiyor
        }
        Tenant t = tenantById.get(e.getTenantId());
        return t == null ? "—" : t.getAd();
    }
}
