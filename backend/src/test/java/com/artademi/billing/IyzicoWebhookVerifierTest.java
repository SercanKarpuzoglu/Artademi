package com.artademi.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.artademi.billing.iyzico.IyzicoWebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * X-IYZ-SIGNATURE-V3 dogrulamasi: hex(HmacSHA256(merchantId+secretKey+eventType+subRef+orderRef
 * +customerRef, secretKey)). Yanlis imza/eksik header reddedilir.
 */
class IyzicoWebhookVerifierTest {

    private static final String SECRET = "test-secret";
    private static final String MERCHANT = "merchant-1";

    private final IyzicoWebhookVerifier verifier = new IyzicoWebhookVerifier(
            new BillingProperties("http://localhost:5173/abonelik",
                    new BillingProperties.Iyzico("https://sandbox-api.iyzipay.com", "k", SECRET,
                            MERCHANT, "plan", "http://cb")));

    @Test
    void dogruImza_kabul() throws Exception {
        String signature = sign(MERCHANT + SECRET
                + "subscription.order.success" + "SUB1" + "ORD1" + "CUST1");

        assertThat(verifier.verify(signature, "subscription.order.success", "SUB1", "ORD1",
                "CUST1")).isTrue();
    }

    @Test
    void yanlisImza_red() {
        assertThat(verifier.verify("bozuk", "subscription.order.success", "SUB1", "ORD1",
                "CUST1")).isFalse();
    }

    @Test
    void headerYok_red() {
        assertThat(verifier.verify(null, "subscription.order.success", "SUB1", "ORD1", "CUST1"))
                .isFalse();
        assertThat(verifier.verify("  ", "subscription.order.success", "SUB1", "ORD1", "CUST1"))
                .isFalse();
    }

    @Test
    void secretYapilandirilmamis_herImzaRed() throws Exception {
        IyzicoWebhookVerifier bosVerifier = new IyzicoWebhookVerifier(
                new BillingProperties("http://x", new BillingProperties.Iyzico(
                        "https://sandbox-api.iyzipay.com", "", "", "", "", "")));
        // Fail-closed: secret bosken hicbir imza (bos anahtarla hesaplanmis dogru HMAC bile) gecmez.
        assertThat(bosVerifier.verify("herhangi", "subscription.order.success", "S", "O", "C"))
                .isFalse();
    }

    @Test
    void farkliAlan_farkliImza_red() throws Exception {
        String signature = sign(MERCHANT + SECRET
                + "subscription.order.success" + "SUB1" + "ORD1" + "CUST1");

        // Ayni imza baska bir order icin gecmez (replay onlemi).
        assertThat(verifier.verify(signature, "subscription.order.success", "SUB1", "ORD2",
                "CUST1")).isFalse();
    }

    private static String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
