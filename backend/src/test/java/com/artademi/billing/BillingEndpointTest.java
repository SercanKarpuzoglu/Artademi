package com.artademi.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.platform.PaymentStatus;
import com.artademi.platform.Plan;
import com.artademi.platform.Subscription;
import com.artademi.platform.SubscriptionRepository;
import com.artademi.platform.SubscriptionStatus;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Billing uc-uca: rol korumasi (ADMIN), ASKIDA tenant'in odeme akisina ERISEBILMESI (muafiyet),
 * webhook imza dogrulama + idempotency, callback baglama, tenant izolasyonu. iyzico API'si MOCK
 * (ag yok); webhook imzasi testte ayni resmi algoritmayla uretilir.
 */
@SpringBootTest(properties = {
        "artademi.billing.web-return-url=http://localhost:5173/abonelik",
        "artademi.billing.iyzico.base-url=https://sandbox-api.iyzipay.com",
        "artademi.billing.iyzico.api-key=test-api-key",
        "artademi.billing.iyzico.secret-key=test-secret",
        "artademi.billing.iyzico.merchant-id=merchant-1",
        "artademi.billing.iyzico.pricing-plan-reference-code=plan-1",
        "artademi.billing.iyzico.callback-url=http://localhost:8081/api/billing/callback"
})
@AutoConfigureMockMvc
@Testcontainers
class BillingEndpointTest {

    static final String SECRET = "test-secret";
    static final String MERCHANT = "merchant-1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    /** iyzico'ya ag cikisi olmasin: saglayici mock'lanir (BillingService port uzerinden calisir). */
    @MockBean
    PaymentProvider provider;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    SubscriptionRepository subRepo;

    @Autowired
    BillingEventRepository eventRepo;

    @BeforeEach
    void stubProvider() {
        given(provider.name()).willReturn("iyzico");
    }

    private static RequestPostProcessor token(UUID tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private Tenant newTenant(TenantStatus status) {
        Tenant t = Tenant.create("Billing Uc " + UUID.randomUUID());
        t.setStatus(status);
        return tenantRepo.save(t);
    }

    private Subscription newSubscription(UUID tenantId) {
        return subRepo.save(Subscription.createTrial(tenantId, LocalDate.now(), 14));
    }

    // ---------- rol korumasi + tenant izolasyonu ----------

    @Test
    void subscription_adminGorur_digerRoller403() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        newSubscription(t.getId());

        mockMvc.perform(get("/api/billing/subscription").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("DENEME"))
                .andExpect(jsonPath("$.data.otomatikOdemeAktif").value(false));

        mockMvc.perform(get("/api/billing/subscription").with(token(t.getId(), "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/billing/subscription").with(token(t.getId(), "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void subscription_tenantIzolasyonu_herkesKendisininkiniGorur() throws Exception {
        Tenant a = newTenant(TenantStatus.AKTIF);
        Subscription subA = newSubscription(a.getId());
        subA.setProviderSubscriptionRef("SUB-A");
        subA.setStatus(SubscriptionStatus.AKTIF);
        subRepo.save(subA);

        Tenant b = newTenant(TenantStatus.AKTIF);
        newSubscription(b.getId());

        // B'nin admin'i A'nin bagli aboneligini DEGIL, kendi trial'ini gorur.
        mockMvc.perform(get("/api/billing/subscription").with(token(b.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("DENEME"))
                .andExpect(jsonPath("$.data.otomatikOdemeAktif").value(false));
    }

    @Test
    void askidaTenant_odemeAkisinaErisebilir() throws Exception {
        // TenantStatusInterceptor /api/billing/** icin MUAF: askidaki kurum odeme yapabilmeli.
        Tenant t = newTenant(TenantStatus.ASKIDA);
        Subscription s = newSubscription(t.getId());
        s.setStatus(SubscriptionStatus.ASKIDA);
        subRepo.save(s);

        mockMvc.perform(get("/api/billing/subscription").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("ASKIDA"));
    }

    // ---------- checkout ----------

    @Test
    void checkout_baslatilir_tokenAbonelijeYazilir() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        Subscription s = newSubscription(t.getId());
        given(provider.startCheckout(any())).willReturn(new CheckoutSession("tok-9", "<form/>"));

        mockMvc.perform(post("/api/billing/checkout")
                        .with(token(t.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ad":"Ada","soyad":"Yılmaz","email":"ada@ornek.com",
                                 "telefon":"+905551112233","kimlikVergiNo":"12345678901",
                                 "adres":"Cadde 1 No 2","sehir":"İstanbul"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("tok-9"));

        assertThat(subRepo.findById(s.getId()).orElseThrow().getCheckoutToken())
                .isEqualTo("tok-9");
    }

    @Test
    void checkout_eksikAlan_400AlanHatasi() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        newSubscription(t.getId());

        mockMvc.perform(post("/api/billing/checkout")
                        .with(token(t.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Ada\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- callback ----------

    @Test
    void callback_basarili_302VeAboneligiBaglar() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        Subscription s = newSubscription(t.getId());
        s.setCheckoutToken("tok-cb");
        subRepo.save(s);
        given(provider.fetchCheckoutResult("tok-cb"))
                .willReturn(new CheckoutResult(true, "SUB-YENI", "CUST-9"));

        mockMvc.perform(post("/api/billing/callback").param("token", "tok-cb"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/abonelik?sonuc=basarili"));

        Subscription after = subRepo.findById(s.getId()).orElseThrow();
        assertThat(after.getProviderSubscriptionRef()).isEqualTo("SUB-YENI");
        assertThat(after.getProvider()).isEqualTo("iyzico");
        assertThat(after.getPlan()).isEqualTo(Plan.AYLIK);
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.ODENDI);
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(after.getCheckoutToken()).isNull();
    }

    @Test
    void callback_bilinmeyenToken_hataYonlendirmesi() throws Exception {
        mockMvc.perform(post("/api/billing/callback").param("token", "tok-yok"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:5173/abonelik?sonuc=hata"));
    }

    // ---------- webhook ----------

    @Test
    void webhook_gecerliImza_odendiYapar_veMukerrerIslemez() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        Subscription s = newSubscription(t.getId());
        s.setProviderSubscriptionRef("SUB-WH");
        s.setPaymentStatus(PaymentStatus.BEKLIYOR);
        subRepo.save(s);

        String body = webhookBody("subscription.order.success", "SUB-WH", "ORD-77");
        String signature = sign("subscription.order.success", "SUB-WH", "ORD-77", "CUST-1");
        long before = eventRepo.count();

        mockMvc.perform(post("/api/webhooks/iyzico")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Subscription after = subRepo.findById(s.getId()).orElseThrow();
        assertThat(after.getPaymentStatus()).isEqualTo(PaymentStatus.ODENDI);
        assertThat(after.getStatus()).isEqualTo(SubscriptionStatus.AKTIF);
        assertThat(eventRepo.count()).isEqualTo(before + 1);

        // Ayni bildirim tekrar gelirse: 200 ama IKINCI KEZ ISLENMEZ (kayit artmaz).
        mockMvc.perform(post("/api/webhooks/iyzico")
                        .header("X-IYZ-SIGNATURE-V3", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        assertThat(eventRepo.count()).isEqualTo(before + 1);
    }

    @Test
    void webhook_failure_basarisizIsaretler() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        Subscription s = newSubscription(t.getId());
        s.setProviderSubscriptionRef("SUB-FAIL");
        subRepo.save(s);

        mockMvc.perform(post("/api/webhooks/iyzico")
                        .header("X-IYZ-SIGNATURE-V3",
                                sign("subscription.order.failure", "SUB-FAIL", "ORD-88", "CUST-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("subscription.order.failure", "SUB-FAIL", "ORD-88")))
                .andExpect(status().isOk());

        assertThat(subRepo.findById(s.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.BASARISIZ);
    }

    @Test
    void webhook_gecersizImza_401VeIslemYok() throws Exception {
        Tenant t = newTenant(TenantStatus.AKTIF);
        Subscription s = newSubscription(t.getId());
        s.setProviderSubscriptionRef("SUB-BAD");
        s.setPaymentStatus(PaymentStatus.BEKLIYOR);
        subRepo.save(s);
        long before = eventRepo.count();

        mockMvc.perform(post("/api/webhooks/iyzico")
                        .header("X-IYZ-SIGNATURE-V3", "sahte-imza")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("subscription.order.success", "SUB-BAD", "ORD-99")))
                .andExpect(status().isUnauthorized());

        assertThat(subRepo.findById(s.getId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.BEKLIYOR);
        assertThat(eventRepo.count()).isEqualTo(before);
    }

    @Test
    void webhook_eslesmeyenAbonelik_200IGNORED() throws Exception {
        // 200 doneriz ki iyzico 3x tekrar firtinasi yapmasin; kayit IGNORED olarak tutulur.
        long before = eventRepo.count();

        mockMvc.perform(post("/api/webhooks/iyzico")
                        .header("X-IYZ-SIGNATURE-V3",
                                sign("subscription.order.success", "SUB-TANIMSIZ", "ORD-55",
                                        "CUST-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("subscription.order.success", "SUB-TANIMSIZ",
                                "ORD-55")))
                .andExpect(status().isOk());

        assertThat(eventRepo.count()).isEqualTo(before + 1);
    }

    // ---------- yardimcilar ----------

    private static String webhookBody(String type, String subRef, String orderRef) {
        return """
                {"iyziEventType":"%s","iyziEventTime":1700000000,
                 "iyziReferenceCode":"IYZ-1","subscriptionReferenceCode":"%s",
                 "orderReferenceCode":"%s","customerReferenceCode":"CUST-1"}
                """.formatted(type, subRef, orderRef);
    }

    /** Resmi algoritma: hex(HmacSHA256(merchantId+secret+eventType+subRef+orderRef+custRef)). */
    private static String sign(String eventType, String subRef, String orderRef, String custRef)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String data = MERCHANT + SECRET + eventType + subRef + orderRef + custRef;
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
