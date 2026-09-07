package com.artademi.basvuru;

import com.artademi.platform.Tenant;
import com.artademi.user.UserService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Yeni basvuru geldiginde kurumun ADMIN kullanicilarina e-posta atar.
 *
 * <p><b>Tasarim karari — bildirim basvuruyu ASLA kaybettirmez:</b> mail gonderimi
 * (Keycloak sorgusu dahil) tamamen try/catch icindedir ve hicbir hatayi yukari
 * firlatmaz. Aksi halde SMTP ya da Keycloak gecici olarak erisilemezken veli formu
 * dolduramaz ve talep kaybolurdu — kaydin durmasi bildirimden ONEMLIDIR.
 *
 * <p>Kurumun kendi adiyla gonderilir ("Bale Akademi <info@artademi.com>"): yonetici
 * kutusunda kendi kurumunu tanir.
 */
@Service
public class BasvuruBildirimService {

    private static final Logger log = LoggerFactory.getLogger(BasvuruBildirimService.class);

    private final JavaMailSender mailSender;
    private final UserService users;
    private final String from;
    private final String smtpUsername;

    public BasvuruBildirimService(JavaMailSender mailSender, UserService users,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.users = users;
        this.from = from;
        this.smtpUsername = smtpUsername;
    }

    /** Yeni basvuruyu kurumun admin'lerine bildirir. Hata olursa yalnizca loglanir. */
    public void yeniBasvuru(Tenant tenant, Basvuru basvuru) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("Başvuru bildirimi atlandı: SMTP yapılandırılmamış (tenant={})",
                    tenant.getId());
            return;
        }
        try {
            List<String> adresler = adminAdresleri();
            if (adresler.isEmpty()) {
                log.warn("Başvuru bildirimi gönderilemedi: kurumun e-postalı admin'i yok "
                        + "(tenant={})", tenant.getId());
                return;
            }
            mailSender.send(hazirla(tenant, basvuru, adresler));
            log.info("Başvuru bildirimi gönderildi (tenant={}, alıcı={})",
                    tenant.getId(), adresler.size());
        } catch (RuntimeException e) {
            // Bilincli olarak yutuluyor: basvuru kaydi zaten atildi, bildirim ikincil.
            log.error("Başvuru bildirimi gönderilemedi (tenant={}): {}",
                    tenant.getId(), e.getMessage());
        }
    }

    /** Kurumun e-posta adresi olan AKTIF admin kullanicilari. */
    private List<String> adminAdresleri() {
        return users.list(true, "ADMIN", null, 0, 50).stream()
                .map(u -> u.email())
                .filter(e -> e != null && !e.isBlank())
                .toList();
    }

    private SimpleMailMessage hazirla(Tenant tenant, Basvuru b, List<String> adresler) {
        String kurum = tenant.getAd();
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(adresler.toArray(String[]::new));
        mail.setFrom(kurum + " <" + from + ">");
        // "Yanitla" dogrudan basvurana yazsin; e-posta yoksa kurumun kendi adresine duser.
        mail.setReplyTo(b.getEmail() != null ? b.getEmail() : from);
        mail.setSubject(kurum + " — yeni ön kayıt başvurusu: " + b.getAd() + " " + b.getSoyad());
        mail.setText("""
                Online ön kayıt formunuzdan yeni bir başvuru geldi:

                Ad Soyad  : %s %s
                Telefon   : %s
                E-posta   : %s
                Veli      : %s
                Branş     : %s

                Mesaj:
                %s

                Başvuruyu panelden görüntüleyip öğrenci kaydına dönüştürebilirsiniz:
                Başvurular ekranı → ilgili satır → "Öğrenciye dönüştür".

                (Bu mail otomatik gönderildi; yanıtla tuşu başvurana yazar.)
                """.formatted(
                b.getAd(), b.getSoyad(),
                b.getTelefon(),
                bosSa(b.getEmail()),
                bosSa(b.getVeliAdi()),
                b.getBrans() != null ? b.getBrans().getAd() : "-",
                bosSa(b.getMesaj())));
        return mail;
    }

    private static String bosSa(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
