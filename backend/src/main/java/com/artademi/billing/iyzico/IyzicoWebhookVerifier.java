package com.artademi.billing.iyzico;

import com.artademi.billing.BillingProperties;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * iyzico abonelik webhook imza dogrulamasi ({@code X-IYZ-SIGNATURE-V3} header'i).
 *
 * <p>Resmi algoritma (docs.iyzico.com/en/advanced/webhook): su alanlar SIRAYLA birlestirilir ve
 * secretKey ile HMAC-SHA256 + hex kodlanir:
 * {@code merchantId + secretKey + eventType + subscriptionReferenceCode + orderReferenceCode
 * + customerReferenceCode}
 *
 * <p>Imza esitligi sabit-zamanli karsilastirmayla yapilir (timing attack onlemi).
 */
@Component
public class IyzicoWebhookVerifier {

    private final BillingProperties props;

    public IyzicoWebhookVerifier(BillingProperties props) {
        this.props = props;
    }

    public boolean verify(String signatureHeader, String eventType,
            String subscriptionReferenceCode, String orderReferenceCode,
            String customerReferenceCode) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        // Secret yapilandirilmamissa (IYZICO_SECRET_KEY bos) HIC kabul etme: bos anahtarla HMAC
        // herkesce hesaplanabilir olurdu — fail-closed.
        if (!props.iyzico().configured()) {
            return false;
        }
        String expected = expectedSignature(eventType, subscriptionReferenceCode,
                orderReferenceCode, customerReferenceCode);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    String expectedSignature(String eventType, String subscriptionReferenceCode,
            String orderReferenceCode, String customerReferenceCode) {
        BillingProperties.Iyzico iyzico = props.iyzico();
        String data = nullSafe(iyzico.merchantId())
                + iyzico.secretKey()
                + nullSafe(eventType)
                + nullSafe(subscriptionReferenceCode)
                + nullSafe(orderReferenceCode)
                + nullSafe(customerReferenceCode);
        return IyzicoAuth.hmacSha256Hex(data, iyzico.secretKey());
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
