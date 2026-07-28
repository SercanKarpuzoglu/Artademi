package com.artademi.billing.iyzico;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * IYZWSv2 header ureticisinin resmi algoritmaya uygunlugu: signature =
 * hex(HmacSHA256(randomKey+uriPath+body, secretKey)); header = "IYZWSv2 " +
 * base64("apiKey:..&randomKey:..&signature:..").
 */
class IyzicoAuthTest {

    @Test
    void authorizationHeader_resmiAlgoritmayaUygun() throws Exception {
        String apiKey = "api-123";
        String secretKey = "secret-456";
        String randomKey = "1700000000000abc";
        String path = "/v2/subscription/checkoutform/initialize";
        String body = "{\"locale\":\"tr\"}";

        String header = IyzicoAuth.authorizationHeader(apiKey, secretKey, randomKey, path, body);

        assertThat(header).startsWith("IYZWSv2 ");
        String decoded = new String(
                Base64.getDecoder().decode(header.substring("IYZWSv2 ".length())),
                StandardCharsets.UTF_8);

        // Bagimsiz hesaplanan imza ile birebir ayni olmali.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expectedSignature = HexFormat.of()
                .formatHex(mac.doFinal((randomKey + path + body).getBytes(StandardCharsets.UTF_8)));

        assertThat(decoded).isEqualTo(
                "apiKey:" + apiKey + "&randomKey:" + randomKey + "&signature:" + expectedSignature);
    }

    @Test
    void getIstegi_bosGovdeIleImzalanir() {
        // GET'te body "" — ayni girdiler ayni header'i uretmeli (deterministik).
        String h1 = IyzicoAuth.authorizationHeader("k", "s", "rnd", "/v2/x", "");
        String h2 = IyzicoAuth.authorizationHeader("k", "s", "rnd", "/v2/x", "");
        assertThat(h1).isEqualTo(h2);
    }
}
