package com.artademi.platform.dto;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.SubscriptionStatus;
import com.artademi.platform.TenantStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform konsolu genel bakis (SADECE SUPER_ADMIN) — platform SAHIBININ isletme gorunumu.
 *
 * <p>⚠️ Bu, tenant'larin kendi ic muhasebesiyle ILGISIZDIR (subscription-billing skill ayrimi):
 * buradaki tutarlar OKULLARIN BIZE odedigi platform geliridir, okulun ogrenciden tahsilati degil.
 *
 * @param kurumlar kurum sayilari (status bazinda)
 * @param abonelikler abonelik sayilari (status bazinda)
 * @param gelir aylik tekrarlayan gelir ozeti
 * @param dikkatGerektirenler aksiyon bekleyen kurumlar (grace / odeme bekleyen)
 * @param yaklasanYenilemeler onumuzdeki 7 gun icinde donemi biten abonelikler
 * @param sonHareketler son odeme/mutabakat izleri (billing_event)
 */
public record PlatformDashboardResponse(
        KurumSayilari kurumlar,
        Map<SubscriptionStatus, Long> abonelikler,
        Gelir gelir,
        List<DikkatSatiri> dikkatGerektirenler,
        List<YenilemeSatiri> yaklasanYenilemeler,
        List<HareketSatiri> sonHareketler) {

    /**
     * @param toplam SILINDI haric toplam kurum
     * @param buAyYeni bu takvim ayinda olusturulan kurum
     * @param statuBazinda her statuden kac kurum var
     */
    public record KurumSayilari(long toplam, long buAyYeni, Map<TenantStatus, Long> statuBazinda) {
    }

    /**
     * @param odeyenKurum otomatik odemesi aktif + odemesi guncel kurum sayisi
     * @param aylikTekrarlayan MRR = odeyenKurum × aylik plan ucreti (ucret yapilandirmadan gelir)
     * @param aylikPlanUcreti tek kurum icin aylik ucret (KDV haric)
     */
    public record Gelir(long odeyenKurum, BigDecimal aylikTekrarlayan, BigDecimal aylikPlanUcreti) {
    }

    /**
     * @param sebep neden dikkat gerektiriyor (GRACE / ODEME_BASARISIZ / ASKIDA)
     */
    public record DikkatSatiri(UUID tenantId, String ad, TenantStatus tenantStatus,
            SubscriptionStatus abonelikStatus, PaymentStatus odemeStatus, LocalDate graceEndsAt,
            String sebep) {
    }

    public record YenilemeSatiri(UUID tenantId, String ad, LocalDate donemBitisi,
            boolean otomatikOdeme) {
    }

    public record HareketSatiri(String eventType, String status, String kurumAdi,
            java.time.Instant tarih) {
    }
}
