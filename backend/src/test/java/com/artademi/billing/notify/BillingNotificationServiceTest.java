package com.artademi.billing.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Hangi uyarinin gonderilecegi karari — saf mantik, bagimliliksiz.
 * Yanlis uyari, uyari gondermemekten daha kotudur (or. askidaki kuruma "ek süreniz var" demek).
 */
class BillingNotificationServiceTest {

    private static final LocalDate BUGUN = LocalDate.of(2026, 9, 10);

    private static Subscription abonelik(SubscriptionStatus st, PaymentStatus pay,
            LocalDate graceEndsAt) {
        Subscription s = Subscription.createTrial(UUID.randomUUID(), LocalDate.of(2026, 8, 1), 14);
        s.setStatus(st);
        s.setPaymentStatus(pay);
        s.setGraceEndsAt(graceEndsAt);
        return s;
    }

    @Test
    void askidaki_askiyaAlindiUyarisi() {
        var s = abonelik(SubscriptionStatus.ASKIDA, PaymentStatus.BEKLIYOR,
                BUGUN.minusDays(1));
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN))
                .contains(BildirimTipi.ASKIYA_ALINDI);
    }

    @Test
    void graceBasinda_graceBasladiUyarisi() {
        // Ek surenin basi: bitise 14 gun var → "ek sureniz var" mesaji.
        var s = abonelik(SubscriptionStatus.ODEME_BEKLIYOR, PaymentStatus.BEKLIYOR,
                BUGUN.plusDays(14));
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN))
                .contains(BildirimTipi.GRACE_BASLADI);
    }

    @Test
    void graceSonuna3GunKala_sonUyari() {
        var s = abonelik(SubscriptionStatus.ODEME_BEKLIYOR, PaymentStatus.BEKLIYOR,
                BUGUN.plusDays(3));
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN))
                .contains(BildirimTipi.GRACE_BITIYOR);
    }

    @Test
    void graceSonGun_sonUyari() {
        var s = abonelik(SubscriptionStatus.ODEME_BEKLIYOR, PaymentStatus.BEKLIYOR, BUGUN);
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN))
                .contains(BildirimTipi.GRACE_BITIYOR);
    }

    @Test
    void tahsilatBasarisiz_amaHenuzGraceYok_basarisizUyarisi() {
        // Abonelik AKTIF ama son cekim patladi → erisim acik, kart guncelleme uyarisi.
        var s = abonelik(SubscriptionStatus.AKTIF, PaymentStatus.BASARISIZ, null);
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN))
                .contains(BildirimTipi.ODEME_BASARISIZ);
    }

    @Test
    void saglikliAbonelik_uyariYOK() {
        var s = abonelik(SubscriptionStatus.AKTIF, PaymentStatus.ODENDI, null);
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN)).isEmpty();
    }

    @Test
    void denemeSurecindeki_uyariYOK() {
        var s = abonelik(SubscriptionStatus.DENEME, PaymentStatus.BEKLIYOR, null);
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN)).isEmpty();
    }

    @Test
    void iptalEdilmis_uyariYOK() {
        var s = abonelik(SubscriptionStatus.IPTAL, PaymentStatus.BEKLIYOR, null);
        assertThat(BillingNotificationService.gerekenBildirim(s, BUGUN)).isEmpty();
    }

    @Test
    void askidaki_graceTarihiGecmisOlsaBile_askiyaAlindiKazanir() {
        // Oncelik testi: ASKIDA durumu her zaman grace mesajlarini EZER.
        var s = abonelik(SubscriptionStatus.ASKIDA, PaymentStatus.BASARISIZ, BUGUN.plusDays(2));
        Optional<BildirimTipi> tip = BillingNotificationService.gerekenBildirim(s, BUGUN);
        assertThat(tip).contains(BildirimTipi.ASKIYA_ALINDI);
    }
}
