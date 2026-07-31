package com.artademi.platform;

import com.artademi.billing.BillingEvent;
import com.artademi.billing.BillingEventRepository;
import com.artademi.platform.dto.BillingEventRow;
import com.artademi.platform.dto.PlatformSubscriptionRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform odeme/abonelik takibi (SADECE SUPER_ADMIN uzerinden cagrilir).
 *
 * <p>Tenant/Subscription platform-duzeyidir (tenant filtresine tabi degil) → duz {@code findAll}
 * dogrudur; bu, findScopedById kuralinin platform istisnasi.
 */
@Service
public class PlatformBillingService {

    /** Odeme takibi filtreleri — is diliyle, teknik statu kombinasyonlarini gizler. */
    public enum Filtre {
        /** Parasi duzenli gelenler. */
        ODEYEN,
        /** Deneme surecinde (henuz para gelmedi). */
        DENEME,
        /** Odeme gecikti ama erisim acik (grace) veya son tahsilat basarisiz. */
        GECIKMIS,
        /** Erisimi kesilmis. */
        ASKIDA,
        /** Silinmis kurumlar dahil hepsi. */
        HEPSI
    }

    private final TenantRepository tenants;
    private final SubscriptionRepository subscriptions;
    private final BillingEventRepository events;

    public PlatformBillingService(TenantRepository tenants, SubscriptionRepository subscriptions,
            BillingEventRepository events) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.events = events;
    }

    /**
     * Kurum bazli abonelik/odeme durumu listesi.
     *
     * @param filtre is-dili filtresi (null = HEPSI ama SILINDI haric)
     * @param q kurum adinda arama (buyuk/kucuk harf duyarsiz)
     */
    @Transactional(readOnly = true)
    public List<PlatformSubscriptionRow> subscriptionRows(Filtre filtre, String q) {
        Map<UUID, Tenant> tenantById = tenants.findAll().stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        Filtre etkin = filtre == null ? Filtre.HEPSI : filtre;
        String arama = q == null ? null : q.trim().toLowerCase();

        return subscriptions.findAll().stream()
                .map(s -> {
                    Tenant t = tenantById.get(s.getTenantId());
                    return t == null ? null : toRow(t, s);
                })
                .filter(java.util.Objects::nonNull)
                // SILINDI kurumlar yalnizca HEPSI'de gorunur (liste kirlenmesin).
                .filter(r -> etkin == Filtre.HEPSI || r.tenantStatus() != TenantStatus.SILINDI)
                .filter(r -> uyar(r, etkin))
                .filter(r -> arama == null || arama.isEmpty()
                        || r.ad().toLowerCase().contains(arama))
                .sorted(Comparator.comparing(PlatformSubscriptionRow::ad,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static PlatformSubscriptionRow toRow(Tenant t, Subscription s) {
        return new PlatformSubscriptionRow(
                t.getId(), t.getAd(), t.getStatus(),
                s.getPlan(), s.getStatus(), s.getPaymentStatus(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getGraceEndsAt(),
                s.getProviderSubscriptionRef() != null);
    }

    private static boolean uyar(PlatformSubscriptionRow r, Filtre filtre) {
        return switch (filtre) {
            case ODEYEN -> r.abonelikStatus() == SubscriptionStatus.AKTIF
                    && r.odemeStatus() == PaymentStatus.ODENDI;
            case DENEME -> r.abonelikStatus() == SubscriptionStatus.DENEME;
            case GECIKMIS -> r.abonelikStatus() == SubscriptionStatus.ODEME_BEKLIYOR
                    || r.odemeStatus() == PaymentStatus.BASARISIZ;
            case ASKIDA -> r.abonelikStatus() == SubscriptionStatus.ASKIDA
                    || r.tenantStatus() == TenantStatus.ASKIDA;
            case HEPSI -> true;
        };
    }

    /** Odeme hareketleri (webhook/mutabakat izleri), en yeni once. */
    @Transactional(readOnly = true)
    public Page<BillingEventRow> events(Pageable pageable) {
        Map<UUID, Tenant> tenantById = tenants.findAll().stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return events.findAll(pageable).map(e -> toRow(e, tenantById));
    }

    private static BillingEventRow toRow(BillingEvent e, Map<UUID, Tenant> tenantById) {
        Tenant t = e.getTenantId() == null ? null : tenantById.get(e.getTenantId());
        return new BillingEventRow(
                e.getId(), e.getProvider(), e.getEventType(), e.getStatus().name(),
                e.getTenantId(), t == null ? null : t.getAd(), e.getCreatedAt());
    }

}
