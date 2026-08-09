package com.artademi.billing.notify;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionStatus;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.platform.TenantUserAdmin;
import com.artademi.platform.dto.TenantUserView;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Odeme/abonelik uyarilarini kuruma e-posta ile bildirir.
 *
 * <p><b>Neden var:</b> uyari olmadan sistem, odemesi alinamayan kurumu 14 gun sonra SESSIZCE
 * askiya aliyordu — kurum durumu ancak panele giremeyince ogreniyordu. Bu, ilk gercek musteride
 * yasanacak en kotu deneyim.
 *
 * <p><b>Idempotency:</b> scheduler her gun calisir; her uyari bir DONEM icinde en fazla bir kez
 * gonderilir ({@code uq_billing_notification}). Bir sonraki donemde ayni uyari yeniden gidebilir.
 *
 * <p><b>Kirilganlik yonetimi:</b> mail/Keycloak hatasi is akisini DURDURMAZ — loglanir ve digerine
 * gecilir. Bildirim gonderilemedi diye abonelik degerlendirmesi aksamamali.
 */
@Service
public class BillingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BillingNotificationService.class);

    /** Ek sure bitisine bu kadar gun (veya daha az) kalinca "son uyari" gider. */
    static final int SON_UYARI_ESIGI_GUN = 3;

    private final SubscriptionRepository subscriptions;
    private final TenantRepository tenants;
    private final TenantUserAdmin tenantUserAdmin;
    private final BillingNotificationRepository gonderilenler;
    private final JavaMailSender mailSender;
    private final String from;
    private final String smtpUsername;
    private final String abonelikUrl;

    public BillingNotificationService(SubscriptionRepository subscriptions,
            TenantRepository tenants, TenantUserAdmin tenantUserAdmin,
            BillingNotificationRepository gonderilenler, JavaMailSender mailSender,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${artademi.billing.web-return-url}") String abonelikUrl) {
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.tenantUserAdmin = tenantUserAdmin;
        this.gonderilenler = gonderilenler;
        this.mailSender = mailSender;
        this.from = from;
        this.smtpUsername = smtpUsername;
        this.abonelikUrl = abonelikUrl;
    }

    /**
     * Tum abonelikleri gozden gecirir ve gerekiyorsa uyari gonderir.
     * Deterministik test icin {@code today} disaridan verilir.
     *
     * @return gonderilen bildirim sayisi
     */
    @Transactional
    public int bildirimleriGonder(LocalDate today) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("Ödeme uyarıları atlandı: SMTP yapılandırılmamış");
            return 0;
        }
        int gonderilen = 0;
        for (Subscription s : subscriptions.findAll()) {
            try {
                if (birAbonelikIcin(s, today)) {
                    gonderilen++;
                }
            } catch (RuntimeException e) {
                // Tek kurumun sorunu digerlerini engellemesin.
                log.error("Ödeme uyarısı gönderilemedi (tenant={}): {}",
                        s.getTenantId(), e.getMessage());
            }
        }
        if (gonderilen > 0) {
            log.info("Ödeme uyarısı gönderildi: {} kurum", gonderilen);
        }
        return gonderilen;
    }

    private boolean birAbonelikIcin(Subscription s, LocalDate today) {
        Optional<BildirimTipi> tipOpt = gerekenBildirim(s, today);
        if (tipOpt.isEmpty()) {
            return false;
        }
        BildirimTipi tip = tipOpt.get();
        String donem = donemAnahtari(s);

        if (gonderilenler.existsBySubscriptionIdAndTipAndDonemAnahtari(s.getId(), tip, donem)) {
            return false; // bu donem icin zaten gonderildi
        }
        Tenant tenant = tenants.findById(s.getTenantId()).orElse(null);
        if (tenant == null || tenant.getStatus() == TenantStatus.SILINDI) {
            return false; // silinmis kuruma uyari gonderilmez
        }

        List<String> aliciEpostalar = yoneticiEpostalari(s.getTenantId());
        if (aliciEpostalar.isEmpty()) {
            // Alici yoksa kaydi YAZMA: yonetici eklenince uyari gidebilsin.
            log.warn("Ödeme uyarısı için alıcı bulunamadı (tenant={}, tip={})",
                    s.getTenantId(), tip);
            return false;
        }

        gonder(tip, tenant, s, aliciEpostalar, today);
        gonderilenler.save(BillingNotification.of(s.getId(), s.getTenantId(), tip, donem,
                String.join(", ", aliciEpostalar)));
        return true;
    }

    /** Hangi uyari gerekiyor? Oncelik: askiya alindi > ek sure bitiyor > ek sure basladi > basarisiz. */
    static Optional<BildirimTipi> gerekenBildirim(Subscription s, LocalDate today) {
        if (s.getStatus() == SubscriptionStatus.ASKIDA) {
            return Optional.of(BildirimTipi.ASKIYA_ALINDI);
        }
        if (s.getStatus() == SubscriptionStatus.ODEME_BEKLIYOR) {
            LocalDate grace = s.getGraceEndsAt();
            if (grace != null && !today.isAfter(grace)
                    && ChronoUnit.DAYS.between(today, grace) <= SON_UYARI_ESIGI_GUN) {
                return Optional.of(BildirimTipi.GRACE_BITIYOR);
            }
            return Optional.of(BildirimTipi.GRACE_BASLADI);
        }
        if (s.getPaymentStatus() == PaymentStatus.BASARISIZ) {
            return Optional.of(BildirimTipi.ODEME_BASARISIZ);
        }
        return Optional.empty();
    }

    /** Donem anahtari: ayni uyari SONRAKI donemde tekrar gonderilebilsin diye donem sonu. */
    private static String donemAnahtari(Subscription s) {
        return s.getCurrentPeriodEnd() == null ? "-" : s.getCurrentPeriodEnd().toString();
    }

    /** Kurumun ADMIN rolundeki, e-postasi tanimli kullanicilari. */
    private List<String> yoneticiEpostalari(UUID tenantId) {
        try {
            return tenantUserAdmin.list(tenantId).stream()
                    .filter(u -> u.roller() != null && u.roller().contains("ADMIN"))
                    .map(TenantUserView::email)
                    .filter(e -> e != null && !e.isBlank())
                    .distinct()
                    .toList();
        } catch (RuntimeException e) {
            log.error("Kurum yöneticileri okunamadı (tenant={}): {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private void gonder(BildirimTipi tip, Tenant tenant, Subscription s, List<String> alicilar,
            LocalDate today) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(alicilar.toArray(String[]::new));
        mail.setFrom(from);
        mail.setReplyTo(from);
        mail.setSubject(konu(tip, tenant));
        mail.setText(govde(tip, tenant, s, today));
        mailSender.send(mail);
    }

    private static String konu(BildirimTipi tip, Tenant tenant) {
        return switch (tip) {
            case ODEME_BASARISIZ -> "Artademi — ödemeniz alınamadı (" + tenant.getAd() + ")";
            case GRACE_BASLADI -> "Artademi — ödeme bekleniyor (" + tenant.getAd() + ")";
            case GRACE_BITIYOR -> "Artademi — son hatırlatma: erişiminiz kapanmak üzere";
            case ASKIYA_ALINDI -> "Artademi — hesabınız askıya alındı (" + tenant.getAd() + ")";
        };
    }

    private String govde(BildirimTipi tip, Tenant tenant, Subscription s, LocalDate today) {
        String kalanGun = s.getGraceEndsAt() == null ? "-"
                : String.valueOf(Math.max(0, ChronoUnit.DAYS.between(today, s.getGraceEndsAt())));
        String alt = "\n\nÖdeme sayfanız: " + abonelikUrl
                + "\nSorularınız için: info@artademi.com\n\nArtademi";

        return switch (tip) {
            case ODEME_BASARISIZ -> """
                    Merhaba,

                    %s aboneliğiniz için yaptığımız tahsilat denemesi başarısız oldu.
                    En sık nedenler: kartın son kullanma tarihinin geçmesi, limit yetersizliği
                    veya bankanın internet ödemelerine kapalı olması.

                    Hizmetiniz şu an AÇIK. Kart bilgilerinizi güncellerseniz tahsilat otomatik
                    olarak tekrar denenecek.""".formatted(tenant.getAd()) + alt;

            case GRACE_BASLADI -> """
                    Merhaba,

                    %s aboneliğinizin ödemesi henüz alınamadı.

                    Hizmetiniz KESİNTİSİZ devam ediyor; ödemeyi tamamlamanız için %s güne kadar
                    süreniz var. Bu süre içinde ödeme alınırsa hiçbir kesinti yaşanmaz."""
                    .formatted(tenant.getAd(), kalanGun) + alt;

            case GRACE_BITIYOR -> """
                    Merhaba,

                    %s aboneliğiniz için tanınan ek süre dolmak üzere: %s gün kaldı.

                    Bu süre sonunda ödeme alınamazsa hesabınız askıya alınacak ve ekibiniz
                    uygulamaya giriş yapamayacak. Verileriniz SİLİNMEZ; ödeme tamamlandığında
                    erişim aynı verilerle yeniden açılır.""".formatted(tenant.getAd(), kalanGun)
                    + alt;

            case ASKIYA_ALINDI -> """
                    Merhaba,

                    %s hesabınız, ödeme alınamadığı için askıya alındı ve ekibiniz şu an
                    uygulamaya giriş yapamıyor.

                    Verileriniz SİLİNMEDİ. Ödemeyi tamamladığınızda hesabınız aynı verilerle
                    anında yeniden açılır — yönetici hesabınızla giriş yapıp ödeme sayfasından
                    işlemi tamamlayabilirsiniz.""".formatted(tenant.getAd()) + alt;
        };
    }
}
