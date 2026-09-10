package com.artademi.bildirim.kanal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** E-posta kanali: SMTP yapilandirilmamissa sessizce kapali (false). */
@Component
public class EpostaKanali implements BildirimKanali {

    private static final Logger log = LoggerFactory.getLogger(EpostaKanali.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String smtpUsername;

    public EpostaKanali(JavaMailSender mailSender,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.from = from;
        this.smtpUsername = smtpUsername;
    }

    @Override
    public String ad() {
        return "e-posta";
    }

    @Override
    public boolean hazir() {
        return smtpUsername != null && !smtpUsername.isBlank();
    }

    @Override
    public boolean gonder(String alici, String konu, String metin) {
        if (!hazir() || alici == null || alici.isBlank()) {
            return false;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(alici);
            mail.setSubject(konu);
            mail.setText(metin);
            mailSender.send(mail);
            return true;
        } catch (RuntimeException e) {
            log.error("E-posta gönderilemedi ({}): {}", alici, e.getMessage());
            return false;
        }
    }
}
