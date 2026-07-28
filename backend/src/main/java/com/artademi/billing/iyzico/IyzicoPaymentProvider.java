package com.artademi.billing.iyzico;

import com.artademi.billing.BillingProperties;
import com.artademi.billing.PaymentProvider;
import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.artademi.common.exception.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public IyzicoPaymentProvider(BillingProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.rest = RestClient.builder().baseUrl(props.iyzico().baseUrl()).build();
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
        JsonNode response = get("/v2/subscription/checkoutform/" + token);
        JsonNode data = response.path("data");
        boolean success = "success".equalsIgnoreCase(response.path("status").asText())
                && !data.path("referenceCode").asText().isBlank();
        return new CheckoutResult(
                success,
                data.path("referenceCode").asText(null),
                data.path("customerReferenceCode").asText(null));
    }

    private JsonNode post(String path, ObjectNode body) {
        String requestBody = body.toString();
        String randomKey = randomKey();
        String auth = IyzicoAuth.authorizationHeader(props.iyzico().apiKey(),
                props.iyzico().secretKey(), randomKey, path, requestBody);
        JsonNode response = rest.post()
                .uri(path)
                .header("Authorization", auth)
                .header("x-iyzi-rnd", randomKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        return requireSuccess(path, response);
    }

    private JsonNode get(String path) {
        String randomKey = randomKey();
        String auth = IyzicoAuth.authorizationHeader(props.iyzico().apiKey(),
                props.iyzico().secretKey(), randomKey, path, "");
        JsonNode response = rest.get()
                .uri(path)
                .header("Authorization", auth)
                .header("x-iyzi-rnd", randomKey)
                .retrieve()
                .body(JsonNode.class);
        return requireSuccess(path, response);
    }

    /** iyzico hata zarfini ({@code status=failure} + errorMessage) is hatasina cevirir. */
    private JsonNode requireSuccess(String path, JsonNode response) {
        if (response == null) {
            throw new ConflictException("iyzico yanıt vermedi: " + path);
        }
        if (!"success".equalsIgnoreCase(response.path("status").asText())) {
            String message = response.path("errorMessage").asText("bilinmeyen hata");
            log.warn("iyzico hata [{}]: {}", path, message);
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
