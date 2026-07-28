package com.artademi.lead;

import com.artademi.common.exception.ConflictException;
import com.artademi.lead.dto.LeadRequest;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Landing form taleplerini info@artademi.com'a mail olarak iletir (Gmail SMTP, app password).
 *
 * <p>Koruma katmanlari (herkese acik uc):
 * <ul>
 *   <li>Honeypot ({@link LeadRequest#botMu()}) — dolu ise sessizce yok sayilir (bota 200 doner).</li>
 *   <li>IP basina soguma: ayni IP {@value #COOLDOWN_SECONDS} sn icinde ikinci talep atamaz (409).</li>
 * </ul>
 *
 * <p>SMTP yapilandirilmamissa ({@code SMTP_USERNAME} bos) 409 doner — form kullaniciya
 * "gonderilemedi" gosterir; talep SESSIZCE kaybolmaz.
 */
@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);
    static final long COOLDOWN_SECONDS = 30;

    private final JavaMailSender mailSender;
    private final String leadTo;
    private final String from;
    private final String smtpUsername;

    /** IP → son talep zamani (naif bellek-ici soguma; tek instance icin yeterli). */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    public LeadService(JavaMailSender mailSender,
            @Value("${artademi.mail.lead-to}") String leadTo,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.leadTo = leadTo;
        this.from = from;
        this.smtpUsername = smtpUsername;
    }

    public void submit(LeadRequest request, String remoteIp) {
        if (request.botMu()) {
            log.info("Lead honeypot yakaladı (ip={}) — yok sayıldı", remoteIp);
            return; // bota basari gibi gorunur, mail gitmez
        }
        throttle(remoteIp);

        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.error("Lead gönderilemedi: SMTP yapılandırılmamış (SMTP_USERNAME boş)");
            throw new ConflictException(
                    "Talebiniz şu an iletilemiyor; lütfen info@artademi.com adresine yazın.");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(leadTo);
        mail.setFrom(from);
        mail.setReplyTo(request.email()); // "Yanıtla" dogrudan talep sahibine gider
        mail.setSubject("[" + request.amac() + "] " + request.kurum() + " — " + request.ad());
        mail.setText("""
                artademi.com iletişim formundan yeni talep:

                Amaç      : %s
                Ad Soyad  : %s
                Kurum     : %s
                E-posta   : %s
                Telefon   : %s

                (Bu mail otomatik gönderildi; yanıtla tuşu talep sahibine yazar.)
                """.formatted(request.amac(), request.ad(), request.kurum(), request.email(),
                request.telefon() == null || request.telefon().isBlank() ? "-"
                        : request.telefon()));
        try {
            mailSender.send(mail);
        } catch (RuntimeException e) {
            log.error("Lead maili gönderilemedi (kurum={}): {}", request.kurum(), e.getMessage());
            throw new ConflictException(
                    "Talebiniz şu an iletilemiyor; lütfen info@artademi.com adresine yazın.");
        }
        log.info("Lead iletildi: {} — {} ({})", request.amac(), request.kurum(), request.email());
    }

    private void throttle(String remoteIp) {
        Instant now = Instant.now();
        Instant previous = lastSeen.put(remoteIp, now);
        if (previous != null
                && Duration.between(previous, now).getSeconds() < COOLDOWN_SECONDS) {
            throw new ConflictException("Çok sık denediniz; lütfen biraz sonra tekrar deneyin.");
        }
        // Naif temizlik: harita buyumesin (landing trafigi icin fazlasiyla yeterli).
        if (lastSeen.size() > 10_000) {
            lastSeen.clear();
        }
    }
}
