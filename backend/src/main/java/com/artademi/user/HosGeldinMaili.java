package com.artademi.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Yeni acilan kullaniciya giris bilgilerini yollar.
 *
 * <p>Onceden bu bilgi yalnizca EKRANDA gosteriliyordu; kullaniciyi acan kisi ilk parolayi elle
 * iletmek zorundaydi (cogu zaman WhatsApp'tan, ya da hic). Artik kullanici kendi mailinden alir.
 *
 * <p>⚠️ Best-effort: mail gonderilemezse kullanici ZATEN OLUSTURULMUSTUR — islem geri alinmaz,
 * cagiran taraf ekranda parolayi gostermeye devam eder (yedek yol korunur).
 */
@Component
public class HosGeldinMaili {

    private static final Logger log = LoggerFactory.getLogger(HosGeldinMaili.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String smtpUsername;
    private final String appUrl;

    public HosGeldinMaili(JavaMailSender mailSender,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${artademi.app-url:https://app.artademi.com}") String appUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.smtpUsername = smtpUsername;
        this.appUrl = appUrl;
    }

    /**
     * @param eposta alicinin adresi; bos ise mail GONDERILMEZ (sessizce atlanir)
     * @param kurumAdi kullanicinin bagli oldugu kurum
     */
    public void gonder(String eposta, String adSoyad, String kullaniciAdi, String ilkParola,
            String kurumAdi) {
        if (smtpUsername == null || smtpUsername.isBlank() || eposta == null || eposta.isBlank()) {
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(eposta);
        mail.setFrom(from);
        mail.setReplyTo(from);
        mail.setSubject("Artademi hesabınız hazır — " + kurumAdi);
        mail.setText("""
                Merhaba %s,

                %s için Artademi hesabınız oluşturuldu. Aşağıdaki bilgilerle giriş yapabilirsiniz:

                Adres        : %s
                Kullanıcı adı: %s
                İlk parola   : %s

                Güvenliğiniz için ilk girişte sizden yeni bir parola belirlemeniz istenecek.
                Parolanızı unutursanız giriş ekranındaki "Şifremi unuttum" bağlantısını
                kullanabilirsiniz.

                Bu hesabı siz talep etmediyseniz lütfen bu e-postayı yok sayın ve kurum
                yöneticinize bilgi verin.

                Artademi
                info@artademi.com
                """.formatted(adSoyad == null || adSoyad.isBlank() ? "" : adSoyad,
                kurumAdi, appUrl, kullaniciAdi, ilkParola));
        try {
            mailSender.send(mail);
            log.info("Hoş geldin maili gönderildi: {} ({})", kullaniciAdi, eposta);
        } catch (RuntimeException e) {
            // Kullanici zaten olusturuldu; mail hatasi islemi geri almaz.
            log.error("Hoş geldin maili gönderilemedi ({}): {}", eposta, e.getMessage());
        }
    }
}
