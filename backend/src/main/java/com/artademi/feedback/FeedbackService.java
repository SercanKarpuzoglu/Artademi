package com.artademi.feedback;

import com.artademi.common.exception.ConflictException;
import com.artademi.feedback.dto.FeedbackRequest;
import com.artademi.platform.TenantService;
import com.artademi.user.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Uygulama ICINDEN gelen musteri geri bildirimlerini info@artademi.com'a iletir.
 *
 * <p>Landing'deki lead formundan FARKI: burada kullanici GIRIS YAPMIS durumda — kim, hangi kurum
 * adina yaziyor kesin bilinir. Bu yuzden ad/e-posta sorulmaz, oturumdan alinir; spam riski de
 * yoktur (honeypot gerekmez), yalnizca kotuye kullanima karsi kisa bir soguma vardir.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** Ayni kullanici bu sure icinde ikinci geri bildirim gonderemez. */
    static final long COOLDOWN_SECONDS = 60;

    private final JavaMailSender mailSender;
    private final CurrentUser currentUser;
    private final TenantService tenantService;
    private final String to;
    private final String from;
    private final String smtpUsername;

    /** kullaniciId → son gonderim (tek instance icin yeterli, naif). */
    private final Map<String, Instant> sonGonderim = new ConcurrentHashMap<>();

    public FeedbackService(JavaMailSender mailSender, CurrentUser currentUser,
            TenantService tenantService,
            @Value("${artademi.mail.lead-to}") String to,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.currentUser = currentUser;
        this.tenantService = tenantService;
        this.to = to;
        this.from = from;
        this.smtpUsername = smtpUsername;
    }

    public void gonder(FeedbackRequest req) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            throw new ConflictException(
                    "Geri bildirim şu an iletilemiyor; lütfen info@artademi.com adresine yazın.");
        }
        throttle(currentUser.sub());

        String kurum = tenantService.currentName();
        String kullanici = currentUser.username();
        String epostaAdresi = req.eposta() == null || req.eposta().isBlank() ? null
                : req.eposta().trim();

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setFrom(from);
        if (epostaAdresi != null) {
            mail.setReplyTo(epostaAdresi); // "Yanitla" dogrudan kullaniciya gitsin
        }
        mail.setSubject("[" + req.tip().etiket() + "] " + (kurum == null ? "-" : kurum));
        mail.setText("""
                Uygulama içinden yeni geri bildirim:

                Tip       : %s
                Kurum     : %s
                Kullanıcı : %s
                Roller    : %s
                E-posta   : %s

                --- Mesaj ---
                %s
                """.formatted(
                req.tip().etiket(),
                kurum == null ? "-" : kurum,
                kullanici == null ? "-" : kullanici,
                String.join(", ", currentUser.realmRoles()),
                epostaAdresi == null ? "(belirtilmedi)" : epostaAdresi,
                req.mesaj().trim()));
        try {
            mailSender.send(mail);
        } catch (RuntimeException e) {
            log.error("Geri bildirim maili gönderilemedi (kurum={}): {}", kurum, e.getMessage());
            throw new ConflictException(
                    "Geri bildirim şu an iletilemiyor; lütfen info@artademi.com adresine yazın.");
        }
        log.info("Geri bildirim iletildi: {} — {} ({})", req.tip(), kurum, kullanici);
    }

    private void throttle(String kullaniciId) {
        Instant now = Instant.now();
        Instant onceki = sonGonderim.put(kullaniciId, now);
        if (onceki != null && Duration.between(onceki, now).getSeconds() < COOLDOWN_SECONDS) {
            throw new ConflictException("Çok sık gönderdiniz; lütfen biraz sonra tekrar deneyin.");
        }
        if (sonGonderim.size() > 10_000) {
            sonGonderim.clear();
        }
    }
}
