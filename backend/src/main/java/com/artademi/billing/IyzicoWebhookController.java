package com.artademi.billing;

import com.artademi.billing.dto.IyzicoWebhookPayload;
import com.artademi.billing.iyzico.IyzicoWebhookVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * iyzico webhook ucu ({@code POST /api/webhooks/iyzico}) — JWT YOK (iyzico sunucudan cagirir),
 * kimlik dogrulama {@code X-IYZ-SIGNATURE-V3} HMAC imzasiyla yapilir. Imza gecersiz → 401 (iyzico
 * tekrar dener); gecerli → 200 (islenemese bile 200 donulur ki 3x tekrar firtinasi olmasin —
 * eslesmeyenler billing_event'e IGNORED yazilir).
 *
 * <p>Ham govde String alinir: imza dogrulamasi ve denetim izi icin bire bir icerik gerekir.
 */
@RestController
@RequestMapping("/api/webhooks")
public class IyzicoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(IyzicoWebhookController.class);

    private final BillingService billing;
    private final IyzicoWebhookVerifier verifier;
    private final ObjectMapper json;

    public IyzicoWebhookController(BillingService billing, IyzicoWebhookVerifier verifier,
            ObjectMapper json) {
        this.billing = billing;
        this.verifier = verifier;
        this.json = json;
    }

    @PostMapping("/iyzico")
    public ResponseEntity<Void> iyzico(
            @RequestHeader(value = "X-IYZ-SIGNATURE-V3", required = false) String signature,
            @RequestBody String rawBody) {
        IyzicoWebhookPayload payload;
        try {
            payload = json.readValue(rawBody, IyzicoWebhookPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("iyzico webhook gövdesi çözümlenemedi");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // ⚠️ iyzico bu ADRESE birden fazla olay ailesi gonderir ve her ailenin IMZA FORMULU FARKLI:
        // odeme olaylari (CHECKOUT_FORM_AUTH, THREE_DS_AUTH...) abonelik olaylarindan baska
        // alanlarla imzalanir. Bizim dogrulayici yalnizca ABONELIK formulunu bilir.
        //
        // Ilgilenmedigimiz olaylari imza dogrulamadan GECIYORUZ ama HICBIR SEY YAPMIYORUZ:
        // durum degistirmedigimiz icin guvenlik riski yok, buna karsilik 401 donup iyzico'nun
        // 3 kez tekrar denemesine (gurultu) sebep olmuyoruz. Durum DEGISTIREN abonelik
        // olaylarinda imza kontrolu ZORUNLU kalir.
        String eventType = payload.iyziEventType();
        if (eventType == null || !eventType.startsWith("subscription.")) {
            log.info("iyzico webhook: ilgilenilmeyen olay tipi, yok sayıldı (eventType={})",
                    eventType);
            return ResponseEntity.ok().build();
        }

        boolean valid = verifier.verify(signature, eventType,
                payload.subscriptionReferenceCode(), payload.orderReferenceCode(),
                payload.customerReferenceCode());
        if (!valid) {
            log.warn("iyzico webhook imzası GEÇERSİZ (eventType={})", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        billing.handleIyzicoWebhook(payload, rawBody);
        return ResponseEntity.ok().build();
    }
}
