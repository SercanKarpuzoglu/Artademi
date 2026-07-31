package com.artademi.billing.iyzico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.artademi.billing.BillingProperties;
import com.artademi.billing.dto.CheckoutCustomer;
import com.artademi.billing.dto.CheckoutResult;
import com.artademi.billing.dto.CheckoutSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * iyzico adaptorunun GERCEK sandbox yanit sekilleriyle dogrulanmasi (ag YOK — MockRestServiceServer).
 *
 * <p>Yanit ornekleri 29.07.2026'da canli sandbox'tan alinmistir:
 * <ul>
 *   <li>initialize → token/checkoutFormContent <b>KOKTE</b> (data altinda DEGIL)</li>
 *   <li>retrieve, odeme tamamlanmamis → {@code status=failure, errorCode=201601}</li>
 *   <li>retrieve, basarili → referenceCode (kok veya data altinda olabilir; ikisi de desteklenir)</li>
 * </ul>
 */
class IyzicoPaymentProviderTest {

    private static final CheckoutCustomer CUSTOMER = new CheckoutCustomer("Ada", "Yılmaz",
            "ada@ornek.com", "+905551112233", "12345678901", "Cadde 1", "İstanbul", "Türkiye");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private IyzicoPaymentProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new IyzicoPaymentProvider(
                new BillingProperties("http://web/abonelik", new BillingProperties.Iyzico(
                        "https://sandbox-api.iyzipay.com", "api-k", "secret-k", "merchant-1",
                        "plan-ref", "https://app.artademi.com/api/billing/callback")),
                new ObjectMapper(), builder);
    }

    @Test
    void startCheckout_kokSeviyeTokenVeFormuOkur() {
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/checkoutform/initialize"))
                .andExpect(method(HttpMethod.POST))
                // IYZWSv2 imzali Authorization + rastgele anahtar her istekte gitmeli.
                .andExpect(header("x-iyzi-rnd", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andRespond(withSuccess("""
                        {"status":"success","token":"tok-abc","checkoutFormContent":"<script>iyziInit</script>"}
                        """, MediaType.APPLICATION_JSON));

        CheckoutSession session = provider.startCheckout(CUSTOMER);

        assertThat(session.token()).isEqualTo("tok-abc");
        assertThat(session.checkoutFormContent()).contains("iyziInit");
        server.verify();
    }

    @Test
    void fetchCheckoutResult_odemeTamamlanmamis_istisnaDegilBasarisizSonuc() {
        // GERCEK sandbox yaniti: form doldurulmadan sorgulanirsa failure/201601 doner.
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/checkoutform/tok-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"failure","errorCode":"201601","errorMessage":"Ödeme formu tamamlanmamış."}
                        """, MediaType.APPLICATION_JSON));

        CheckoutResult result = provider.fetchCheckoutResult("tok-1");

        assertThat(result.success()).isFalse();
        assertThat(result.subscriptionReferenceCode()).isNull();
    }

    @Test
    void fetchCheckoutResult_dataAltindaReferans_okunur() {
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/checkoutform/tok-2"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"referenceCode":"SUB-1","customerReferenceCode":"CUST-1"}}
                        """, MediaType.APPLICATION_JSON));

        CheckoutResult result = provider.fetchCheckoutResult("tok-2");

        assertThat(result.success()).isTrue();
        assertThat(result.subscriptionReferenceCode()).isEqualTo("SUB-1");
        assertThat(result.customerReferenceCode()).isEqualTo("CUST-1");
    }

    @Test
    void fetchSubscriptionState_gercekYanit_enIleriBasariliDonemiOkur() {
        // GERCEK sandbox govdesi (2026-07-31): iki siparis — biri SUCCESS (gecmis donem),
        // biri WAITING (siradaki donem). "Odenmis donem sonu" SUCCESS olanin endPeriod'u.
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/subscriptions/SUB-9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"subscriptionStatus":"ACTIVE","orders":[
                          {"orderStatus":"WAITING","price":10000.0,"startPeriod":1788156888051,"endPeriod":1790748888051},
                          {"orderStatus":"SUCCESS","price":10000.0,"startPeriod":1785478488051,"endPeriod":1788156888051}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var state = provider.fetchSubscriptionState("SUB-9").orElseThrow();

        assertThat(state.aktif()).isTrue();
        assertThat(state.sonOdemeBasarili()).isTrue();
        // 1788156888051 ms → 2026-08-27 (WAITING'in ileri tarihi SEÇİLMEMELİ)
        assertThat(state.odenmisDonemSonu())
                .isEqualTo(java.time.Instant.ofEpochMilli(1788156888051L)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    void fetchSubscriptionState_basariliSiparisYok_odemeYokSayilir() {
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/subscriptions/SUB-8"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"subscriptionStatus":"CANCELED","orders":[
                          {"orderStatus":"FAILED","startPeriod":1785478488051,"endPeriod":1788156888051}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var state = provider.fetchSubscriptionState("SUB-8").orElseThrow();

        assertThat(state.aktif()).isFalse();
        assertThat(state.sonOdemeBasarili()).isFalse();
        assertThat(state.odenmisDonemSonu()).isNull();
    }

    @Test
    void fetchSubscriptionState_saglayiciHatasi_bosDoner() {
        // Mutabakat bu durumda kayda DOKUNMAMALI → Optional.empty sozlesmesi.
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/subscriptions/YOK"))
                .andRespond(withSuccess("""
                        {"status":"failure","errorCode":"200000","errorMessage":"Abonelik bulunamadı."}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.fetchSubscriptionState("YOK")).isEmpty();
    }

    @Test
    void fetchCheckoutResult_kokSeviyeReferans_okunur() {
        // initialize kokte donuyordu; retrieve'in de kok donme ihtimaline karsi tolerans.
        server.expect(requestTo("https://sandbox-api.iyzipay.com/v2/subscription/checkoutform/tok-3"))
                .andRespond(withSuccess("""
                        {"status":"success","referenceCode":"SUB-2","customerReferenceCode":"CUST-2"}
                        """, MediaType.APPLICATION_JSON));

        CheckoutResult result = provider.fetchCheckoutResult("tok-3");

        assertThat(result.success()).isTrue();
        assertThat(result.subscriptionReferenceCode()).isEqualTo("SUB-2");
    }
}
