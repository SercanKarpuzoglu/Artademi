package com.artademi.billing.iyzico;

import com.artademi.billing.BillingProperties;
import com.artademi.billing.PaymentProvider;
import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.billing.dto.ProviderSubscriptionState;
import com.artademi.common.exception.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * iyzico Abonelik API adaptörü ({@link PaymentProvider} implementasyonu).
 *
 * <p>Kullanilan uclar (docs.iyzico.com):
 * <ul>
 *   <li>{@code POST /v2/subscription/checkoutform/initialize} — barindirilan abonelik formu baslat</li>
 *   <li>{@code GET /v2/subscription/checkoutform/{token}} — form sonucunu dogrula</li>
 * </ul>
 *
 * <p>Anahtarlar yapilandirilmamissa ({@code IYZICO_API_KEY} bos) checkout 409 doner — uygulama
 * anahtarsiz da ayaga kalkar (dev/test'te bu bean mock'lanir).
 */
@Component
public class IyzicoPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(IyzicoPaymentProvider.class);

    private final BillingProperties props;
    private final RestClient rest;
    private final ObjectMapper json;

    // Iki constructor var (digeri test kancasi) → Spring'e hangisini kullanacagini acikca soyle.
    @Autowired
    public IyzicoPaymentProvider(BillingProperties props, ObjectMapper json) {
        this(props, json, RestClient.builder());
    }

    /** Test kancasi: MockRestServiceServer bagli builder ile ag'siz dogrulama. */
    IyzicoPaymentProvider(BillingProperties props, ObjectMapper json, RestClient.Builder builder) {
        this.props = props;
        this.json = json;
        this.rest = builder.baseUrl(props.iyzico().baseUrl()).build();
    }

    @Override
    public String name() {
        return "iyzico";
    }

    @Override
    public CheckoutSession startCheckout(CheckoutCustomer customer) {
        requireConfigured();
        BillingProperties.Iyzico iyzico = props.iyzico();

        ObjectNode body = json.createObjectNode();
        body.put("locale", "tr");
        body.put("pricingPlanReferenceCode", iyzico.pricingPlanReferenceCode());
        body.put("subscriptionInitialStatus", "ACTIVE");
        body.put("callbackUrl", iyzico.callbackUrl());
        ObjectNode c = body.putObject("customer");
        c.put("name", customer.ad());
        c.put("surname", customer.soyad());
        c.put("email", customer.email());
        c.put("gsmNumber", customer.telefon());
        c.put("identityNumber", customer.kimlikVergiNo());
        ObjectNode billing = c.putObject("billingAddress");
        billing.put("contactName", customer.ad() + " " + customer.soyad());
        billing.put("address", customer.adres());
        billing.put("city", customer.sehir());
        billing.put("country", customer.ulke());

        JsonNode response = post("/v2/subscription/checkoutform/initialize", body);
        return new CheckoutSession(
                response.path("token").asText(),
                response.path("checkoutFormContent").asText());
    }

    @Override
    public CheckoutResult fetchCheckoutResult(String token) {
        requireConfigured();
        // Odeme tamamlanmamis/iptal edilmisse iyzico status=failure doner (or. 201601 "Ödeme formu
        // tamamlanmamış") — bu bir HATA DEGIL, "basarisiz sonuc"tur: istisna firlatmayiz ki
        // callback akisi deterministik kalsin ve kullanici hata ekranina duzgun yonlensin.
        JsonNode response = getRaw("/v2/subscription/checkoutform/" + token);
        if (response == null || !"success".equalsIgnoreCase(response.path("status").asText())) {
            String message = response == null ? "yanıt yok" : response.path("errorMessage").asText("");
            log.info("iyzico checkout sonucu başarısız (token={}): {}", token, message);
            return new CheckoutResult(false, null, null);
        }
        // iyzico bu ailede alanlari bazen kokte, bazen data altinda doner (initialize kokte doner);
        // ikisini de destekle.
        JsonNode body = response.has("data") ? response.path("data") : response;
        String subRef = body.path("referenceCode").asText(null);
        return new CheckoutResult(
                subRef != null && !subRef.isBlank(),
                subRef,
                body.path("customerReferenceCode").asText(null));
    }

    /**
     * Abonelik durumunu iyzico'dan sorgular (mutabakat).
     *
     * <p>Canli sandbox yaniti (2026-07-31): {@code data.subscriptionStatus} = ACTIVE/CANCELED/…
     * ve {@code data.orders[]} her donem icin {@code orderStatus} (SUCCESS/WAITING/FAILED) +
     * {@code startPeriod}/{@code endPeriod} (epoch ms). "Odenmis donem sonu" = en ileri
     * {@code endPeriod}'a sahip SUCCESS siparisin bitisi.
     */
    @Override
    public Optional<ProviderSubscriptionState> fetchSubscriptionState(String subscriptionRef) {
        if (!props.iyzico().configured() || subscriptionRef == null || subscriptionRef.isBlank()) {
            return Optional.empty();
        }
        JsonNode response = getRaw("/v2/subscription/subscriptions/" + subscriptionRef);
        if (response == null || !"success".equalsIgnoreCase(response.path("status").asText())) {
            log.warn("iyzico abonelik sorgusu başarısız (ref={}): {}", subscriptionRef,
                    response == null ? "yanıt yok" : response.path("errorMessage").asText(""));
            return Optional.empty();
        }
        JsonNode data = response.has("data") ? response.path("data") : response;

        boolean aktif = "ACTIVE".equalsIgnoreCase(data.path("subscriptionStatus").asText());
        Long enIleriBasariliBitis = null;
        for (JsonNode order : data.path("orders")) {
            if (!"SUCCESS".equalsIgnoreCase(order.path("orderStatus").asText())) {
                continue;
            }
            long bitis = order.path("endPeriod").asLong(0);
            if (bitis > 0 && (enIleriBasariliBitis == null || bitis > enIleriBasariliBitis)) {
                enIleriBasariliBitis = bitis;
            }
        }
        LocalDate odenmisDonemSonu = enIleriBasariliBitis == null ? null
                : Instant.ofEpochMilli(enIleriBasariliBitis).atZone(ZoneId.systemDefault())
                        .toLocalDate();
        return Optional.of(new ProviderSubscriptionState(
                aktif, enIleriBasariliBitis != null, odenmisDonemSonu));
    }

    /** iyzico aboneligini iptal eder: bekleyen tahsilatlar SUBSCRIPTION_CANCELED'a duser. */
    @Override
    public boolean cancelSubscription(String subscriptionRef) {
        if (!props.iyzico().configured() || subscriptionRef == null || subscriptionRef.isBlank()) {
            return false;
        }
        JsonNode response = post("/v2/subscription/subscriptions/" + subscriptionRef + "/cancel",
                json.createObjectNode().put("locale", "tr"));
        return "success".equalsIgnoreCase(response.path("status").asText());
    }

    private JsonNode post(String path, ObjectNode body) {
        String requestBody = body.toString();
        String randomKey = randomKey();
        String auth = IyzicoAuth.authorizationHeader(props.iyzico().apiKey(),
                props.iyzico().secretKey(), randomKey, path, requestBody);
        JsonNode response = exchange(path, () -> rest.post()
                .uri(path)
                .header("Authorization", auth)
                .header("x-iyzi-rnd", randomKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                // 4xx/5xx'te varsayilan davranis istisna firlatmak: govdeyi GORMEDEN 500'e
                // donusurdu. Durumu yut, govdeyi cagirana birak (asagida is hatasina cevrilir).
                .onStatus(s -> true, (req, res) -> { })
                .body(JsonNode.class));
        return requireSuccess(path, response);
    }

    /** GET — ham yanit (status kontrolu YAPILMAZ; cagiran failure'i is durumu olarak yorumlar). */
    private JsonNode getRaw(String path) {
        String randomKey = randomKey();
        String auth = IyzicoAuth.authorizationHeader(props.iyzico().apiKey(),
                props.iyzico().secretKey(), randomKey, path, "");
        return exchange(path, () -> rest.get()
                .uri(path)
                .header("Authorization", auth)
                .header("x-iyzi-rnd", randomKey)
                .retrieve()
                .onStatus(s -> true, (req, res) -> { })
                .body(JsonNode.class));
    }

    /**
     * Ag/protokol hatalarini opak 500 yerine ANLASILIR is hatasina cevirir ve tanilanabilir
     * sekilde loglar (aksi halde "beklenmeyen hata" ile kor kaliniyordu).
     */
    private JsonNode exchange(String path, java.util.function.Supplier<JsonNode> call) {
        try {
            return call.get();
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("iyzico çağrısı başarısız [{}]: {}", path, e.getMessage());
            throw new ConflictException(
                    "Ödeme sağlayıcısına ulaşılamadı. Lütfen tekrar deneyin.");
        }
    }

    /** iyzico hata zarfini ({@code status=failure} + errorMessage) is hatasina cevirir. */
    private JsonNode requireSuccess(String path, JsonNode response) {
        if (response == null) {
            throw new ConflictException("iyzico yanıt vermedi: " + path);
        }
        if (!"success".equalsIgnoreCase(response.path("status").asText())) {
            String message = response.path("errorMessage").asText("bilinmeyen hata");
            String code = response.path("errorCode").asText("");
            // Tanilama icin HAM yanit da loglanir: iyzico hata kodlari mesajdan daha ayirt edici.
            log.warn("iyzico hata [{}] kod={} mesaj={} | ham={}", path, code, message, response);
            throw new ConflictException("iyzico işlemi başarısız: " + message);
        }
        return response;
    }

    private void requireConfigured() {
        if (!props.iyzico().configured()) {
            throw new ConflictException(
                    "Ödeme sağlayıcı yapılandırılmamış (IYZICO_API_KEY / IYZICO_SECRET_KEY eksik)");
        }
    }

    private static String randomKey() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }
}
