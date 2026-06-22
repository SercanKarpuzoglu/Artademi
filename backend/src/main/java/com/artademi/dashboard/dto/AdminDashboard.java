package com.artademi.dashboard.dto;

import com.artademi.platform.dto.SubscriptionWarning;
import java.math.BigDecimal;
import java.util.List;

/** ADMIN paneli — tam finans gorunurlugu (tahsilat/gider/net/borc + 6 ay trend + grace uyarisi). */
public record AdminDashboard(
        String rol,
        Sayilar sayilar,
        List<Trend> trend6Ay,
        List<OdemeOzet> sonOdemeler,
        List<OgrenciOzet> sonOgrenciler,
        List<DersOzet> bugunDersler,
        SubscriptionWarning subscriptionWarning) implements DashboardData {

    public record Sayilar(
            long aktifOgrenci,
            long aktifGrup,
            BigDecimal buAyTahsilat,
            BigDecimal buAyGider,
            BigDecimal buAyNet,
            BigDecimal bekleyenBorcToplam) {
    }

    public record Trend(String donem, BigDecimal tahsilat, BigDecimal gider, BigDecimal net) {
    }
}
