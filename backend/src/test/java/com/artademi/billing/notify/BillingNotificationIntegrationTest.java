package com.artademi.billing.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionStatus;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.platform.TenantUserAdmin;
import com.artademi.platform.dto.TenantUserView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bildirim gonderiminin UCTAN UCA davranisi: mukerrer gonderim OLMAMALI (scheduler her gun
 * calisiyor), alici yoksa iz yazilmamali, silinmis kuruma uyari gitmemeli.
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@Testcontainers
class BillingNotificationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

    @MockBean
    TenantUserAdmin tenantUserAdmin;

    @Autowired
    BillingNotificationService service;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    SubscriptionRepository subRepo;

    @Autowired
    BillingNotificationRepository notifRepo;

    private static final LocalDate BUGUN = LocalDate.of(2026, 9, 10);

    private Tenant tenant(TenantStatus status) {
        Tenant t = Tenant.create("Bildirim " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private Subscription graceAbonelik(UUID tenantId) {
        Subscription s = Subscription.createTrial(tenantId, LocalDate.of(2026, 8, 1), 14);
        s.setStatus(SubscriptionStatus.ODEME_BEKLIYOR);
        s.setPlan(Plan.AYLIK);
        s.setPaymentStatus(PaymentStatus.BEKLIYOR);
        s.setCurrentPeriodEnd(LocalDate.of(2026, 9, 1));
        s.setGraceEndsAt(BUGUN.plusDays(10));
        return subRepo.save(s);
    }

    private void yoneticiVar(UUID tenantId, String email) {
        given(tenantUserAdmin.list(tenantId)).willReturn(List.of(new TenantUserView(
                "kc-1", "yonetici", "Ada", "Yılmaz", email, null, List.of("ADMIN"), true)));
    }

    @Test
    void uyariGonderilir_veAyniDonemdeTEKRARGONDERILMEZ() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = graceAbonelik(t.getId());
        yoneticiVar(t.getId(), "yonetici@ornek.com");

        assertThat(service.bildirimleriGonder(BUGUN)).isEqualTo(1);
        verify(mailSender).send(any(SimpleMailMessage.class));

        // Scheduler ertesi gun yine calisir — AYNI uyari tekrar GITMEMELI.
        assertThat(service.bildirimleriGonder(BUGUN.plusDays(1))).isZero();
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        assertThat(notifRepo.findAll())
                .filteredOn(n -> n.getSubscriptionId().equals(s.getId()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getTip()).isEqualTo(BildirimTipi.GRACE_BASLADI);
                    assertThat(n.getAlici()).contains("yonetici@ornek.com");
                });
    }

    @Test
    void aliciYoksa_izYAZILMAZ_yoneticiEklenincegonderilebilsin() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = graceAbonelik(t.getId());
        given(tenantUserAdmin.list(t.getId())).willReturn(List.of()); // yonetici yok

        assertThat(service.bildirimleriGonder(BUGUN)).isZero();
        assertThat(notifRepo.findAll())
                .noneMatch(n -> n.getSubscriptionId().equals(s.getId()));
    }

    @Test
    void silinmisKuruma_uyariGONDERILMEZ() {
        Tenant t = tenant(TenantStatus.SILINDI);
        graceAbonelik(t.getId());
        yoneticiVar(t.getId(), "yonetici@ornek.com");

        assertThat(service.bildirimleriGonder(BUGUN)).isZero();
        verifyNoInteractions(mailSender);
    }

    @Test
    void durumDegisince_YENIuyariGonderilir() {
        Tenant t = tenant(TenantStatus.AKTIF);
        Subscription s = graceAbonelik(t.getId());
        yoneticiVar(t.getId(), "yonetici@ornek.com");

        assertThat(service.bildirimleriGonder(BUGUN)).isEqualTo(1); // GRACE_BASLADI

        // Ek sure doldu, hesap askiya alindi → AYRI bir uyari gitmeli.
        s.setStatus(SubscriptionStatus.ASKIDA);
        subRepo.save(s);

        assertThat(service.bildirimleriGonder(BUGUN.plusDays(11))).isEqualTo(1);
        assertThat(notifRepo.findAll())
                .filteredOn(n -> n.getSubscriptionId().equals(s.getId()))
                .extracting(BillingNotification::getTip)
                .containsExactlyInAnyOrder(BildirimTipi.GRACE_BASLADI, BildirimTipi.ASKIYA_ALINDI);
    }
}
