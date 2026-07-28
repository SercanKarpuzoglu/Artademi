package com.artademi.billing.iyzico;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * iyzico API v2 kimlik dogrulama (IYZWSv2 / HMACSHA256) — resmi algoritma:
 *
 * <ol>
 *   <li>{@code signature = hex(HmacSHA256(randomKey + uriPath + requestBody, secretKey))}</li>
 *   <li>{@code authString = "apiKey:" + apiKey + "&randomKey:" + randomKey + "&signature:" + signature}</li>
 *   <li>{@code Authorization: IYZWSv2 " + base64(authString)}; ayrica {@code x-iyzi-rnd: randomKey}</li>
 * </ol>
 *
 * <p>GET isteklerinde requestBody bos string'dir. uriPath, query string DAHIL path'tir
 * (ör. {@code /v2/subscription/checkoutform/{token}}).
 */
final class IyzicoAuth {

    private IyzicoAuth() {
    }

    static String authorizationHeader(String apiKey, String secretKey, String randomKey,
            String uriPath, String requestBody) {
        String signature = hmacSha256Hex(randomKey + uriPath + requestBody, secretKey);
        String authString = "apiKey:" + apiKey + "&randomKey:" + randomKey
                + "&signature:" + signature;
        return "IYZWSv2 " + Base64.getEncoder()
                .encodeToString(authString.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMACSHA256 hesaplanamadı", e);
        }
    }
}
