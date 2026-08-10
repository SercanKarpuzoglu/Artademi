package com.artademi.user;

import com.artademi.platform.TenantUserAdmin;
import com.artademi.platform.dto.TenantUserView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Bir kullanici KENDI hesabini sildiginde kurum yoneticilerine ve platforma bilgi verir.
 *
 * <p>Neden: hesap silme sessiz olmamali — kurum yoneticisi ekibinden birinin ayrildigini
 * ogrenmeli (yetki/erisim gozden gecirilsin), platform da destek/denetim icin bilmeli.
 *
 * <p>Mail gonderimi BEST-EFFORT: patlarsa silme islemi geri alinmaz (kullanici hesabini silme
 * hakkini kullandi; mail altyapisi yuzunden bu hak engellenemez). Hata loglanir.
 */
@Component
public class HesapSilmeBildirimi {

    private static final Logger log = LoggerFactory.getLogger(HesapSilmeBildirimi.class);

    private final JavaMailSender mailSender;
    private final TenantUserAdmin tenantUserAdmin;
    private final String from;
    private final String platformTo;
    private final String smtpUsername;

    public HesapSilmeBildirimi(JavaMailSender mailSender, TenantUserAdmin tenantUserAdmin,
            @Value("${artademi.mail.from}") String from,
            @Value("${artademi.mail.lead-to}") String platformTo,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.tenantUserAdmin = tenantUserAdmin;
        this.from = from;
        this.platformTo = platformTo;
        this.smtpUsername = smtpUsername;
    }

    public void gonder(UserService.SilinenKullanici silinen, String kurumAdi) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("Hesap silme bildirimi atlandı: SMTP yapılandırılmamış");
            return;
        }
        List<String> alicilar = new ArrayList<>();
        alicilar.add(platformTo); // platform her zaman haberdar olur
        alicilar.addAll(kurumYoneticileri(silinen.tenantId()));

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(alicilar.toArray(String[]::new));
        mail.setFrom(from);
        mail.setSubject("Artademi — kullanıcı hesabını sildi (" + kurumAdi + ")");
        mail.setText("""
                Bir kullanıcı Artademi hesabını kendisi sildi.

                Kurum      : %s
                Kullanıcı  : %s
                Ad Soyad   : %s
                E-posta    : %s

                ÖNEMLİ: Yalnızca kullanıcının giriş hesabı silinmiştir.
                Kuruma ait iş verileri (öğrenci kayıtları, yoklamalar, tahsilatlar, hakedişler)
                SİLİNMEMİŞTİR; bunlar kuruma aittir ve panelde durmaya devam eder.

                Bu kullanıcının yetkilerini ve devretmesi gereken işleri gözden geçirmenizi öneririz.

                Artademi
                """.formatted(kurumAdi, silinen.kullaniciAdi(), silinen.adSoyad(),
                silinen.email() == null ? "-" : silinen.email()));
        try {
            mailSender.send(mail);
            log.info("Hesap silme bildirimi gönderildi (kurum={}, kullanici={})",
                    kurumAdi, silinen.kullaniciAdi());
        } catch (RuntimeException e) {
            // Silme zaten yapildi; bildirim gonderilemedi diye geri alinmaz.
            log.error("Hesap silme bildirimi gönderilemedi (kurum={}): {}", kurumAdi,
                    e.getMessage());
        }
    }

    private List<String> kurumYoneticileri(UUID tenantId) {
        try {
            return tenantUserAdmin.list(tenantId).stream()
                    .filter(u -> u.roller() != null && u.roller().contains("ADMIN"))
                    .map(TenantUserView::email)
                    .filter(e -> e != null && !e.isBlank())
                    .distinct()
                    .toList();
        } catch (RuntimeException e) {
            log.error("Kurum yöneticileri okunamadı (tenant={}): {}", tenantId, e.getMessage());
            return List.of();
        }
    }
}
