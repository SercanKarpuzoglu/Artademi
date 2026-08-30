package com.artademi.reminder;

import com.artademi.common.exception.ValidationException;
import com.artademi.platform.TenantService;
import com.artademi.reminder.dto.BorcluAday;
import com.artademi.reminder.dto.HatirlatmaSonucu;
import com.artademi.report.ReportService;
import com.artademi.report.dto.StudentBalanceRow;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borclu ogrencinin VELISINE odeme hatirlatmasi gonderir.
 *
 * <p><b>Neden ELLE tetiklenir:</b> otomatik borc takibi, okulun velisiyle iliskisini yonetmesini
 * elinden alir. Hangi veliye ne zaman yazilacagina okul karar vermeli; sistem yalnizca listeyi
 * hazirlar ve gonderimi kolaylastirir.
 *
 * <p><b>Iki koruma — ikisi de itibar icin:</b>
 * <ol>
 *   <li><b>Soguma ({@value #SOGUMA_GUN} gun):</b> ayni veliye ust uste hatirlatma gitmez.</li>
 *   <li><b>Gunluk tavan ({@value #GUNLUK_TAVAN}):</b> tek seferde yuzlerce mail atilamaz.</li>
 * </ol>
 * Mailler bizim alan adimizdan gidiyor; veliler spam isaretlerse KENDI odeme uyarilarimiz da
 * spam'e duser. Bu yuzden gonderim bilincli olarak yavas ve sinirlidir.
 */
@Service
public class BorcHatirlatmaService {

    private static final Logger log = LoggerFactory.getLogger(BorcHatirlatmaService.class);

    /** Ayni ogrenci icin iki hatirlatma arasindaki asgari sure. */
    static final int SOGUMA_GUN = 7;
    /** Tek istekte gonderilebilecek azami hatirlatma. */
    static final int GUNLUK_TAVAN = 50;

    private final ReportService reportService;
    private final StudentRepository students;
    private final BorcHatirlatmaRepository izler;
    private final TenantService tenantService;
    private final JavaMailSender mailSender;
    private final String from;
    private final String smtpUsername;

    public BorcHatirlatmaService(ReportService reportService, StudentRepository students,
            BorcHatirlatmaRepository izler, TenantService tenantService,
            JavaMailSender mailSender,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.reportService = reportService;
        this.students = students;
        this.izler = izler;
        this.tenantService = tenantService;
        this.mailSender = mailSender;
        this.from = from;
        this.smtpUsername = smtpUsername;
    }

    /** Borclu ogrenciler + her biri icin "gonderilebilir mi, neden degil" bilgisi. */
    @Transactional(readOnly = true)
    public List<BorcluAday> adaylar() {
        Map<Long, Student> ogrenciler = new HashMap<>();
        students.findAll().forEach(s -> ogrenciler.put(s.getId(), s));

        Set<Long> yakindaGonderilenler = new HashSet<>(izler.sonGonderilenOgrenciIdleri(sogumaSiniri()));

        List<BorcluAday> sonuc = new ArrayList<>();
        for (StudentBalanceRow r : borcluSatirlar()) {
            Student s = ogrenciler.get(r.ogrenciId());
            String mail = s == null ? null : s.getVeliMail();
            String engel = null;
            if (mail == null || mail.isBlank()) {
                engel = "Veli e-postası tanımlı değil";
            } else if (yakindaGonderilenler.contains(r.ogrenciId())) {
                engel = "Son " + SOGUMA_GUN + " gün içinde zaten hatırlatıldı";
            }
            sonuc.add(new BorcluAday(r.ogrenciId(), (r.ad() + " " + r.soyad()).trim(), r.bakiye(),
                    mail, null, engel == null, engel));
        }
        return sonuc;
    }

    /**
     * Secilen ogrencilerin velilerine hatirlatma gonderir.
     *
     * <p>Kismi basari normaldir: mailsiz veya soguma icindeki ogrenci ATLANIR ve sebebi
     * sonucta bildirilir — sessizce yok sayilmaz, yonetici neden gitmedigini gorur.
     */
    @Transactional
    public HatirlatmaSonucu gonder(List<Long> ogrenciIdleri) {
        if (ogrenciIdleri == null || ogrenciIdleri.isEmpty()) {
            throw new ValidationException("En az bir öğrenci seçmelisiniz");
        }
        if (ogrenciIdleri.size() > GUNLUK_TAVAN) {
            throw new ValidationException(
                    "Tek seferde en fazla " + GUNLUK_TAVAN + " hatırlatma gönderilebilir.");
        }
        if (smtpUsername == null || smtpUsername.isBlank()) {
            throw new ValidationException("E-posta gönderimi şu an yapılandırılmamış.");
        }

        Set<Long> secilenler = new HashSet<>(ogrenciIdleri);
        String kurum = tenantService.currentName();
        String gonderen = kullaniciAdi();

        List<HatirlatmaSonucu.Satir> satirlar = new ArrayList<>();
        int gonderilen = 0;
        for (BorcluAday aday : adaylar()) {
            if (!secilenler.contains(aday.ogrenciId())) {
                continue;
            }
            if (!aday.gonderilebilir()) {
                satirlar.add(new HatirlatmaSonucu.Satir(aday.ogrenciId(), aday.adSoyad(), false,
                        aday.engelSebebi()));
                continue;
            }
            try {
                mailGonder(aday, kurum);
                izler.save(BorcHatirlatma.of(aday.ogrenciId(), aday.bakiye(), aday.veliMail(),
                        gonderen));
                gonderilen++;
                satirlar.add(new HatirlatmaSonucu.Satir(aday.ogrenciId(), aday.adSoyad(), true,
                        aday.veliMail() + " adresine gönderildi"));
            } catch (RuntimeException e) {
                log.error("Borç hatırlatması gönderilemedi (ogrenci={}): {}",
                        aday.ogrenciId(), e.getMessage());
                satirlar.add(new HatirlatmaSonucu.Satir(aday.ogrenciId(), aday.adSoyad(), false,
                        "Gönderilemedi, daha sonra tekrar deneyin"));
            }
        }
        return new HatirlatmaSonucu(gonderilen, satirlar.size() - gonderilen, satirlar);
    }

    private void mailGonder(BorcluAday aday, String kurum) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(aday.veliMail());
        // Gonderen ADI kurum olsun: veli "Artademi"yi degil KENDI OKULUNU tanir; taninmayan
        // gonderen spam isaretlenme olasiligini ciddi bicimde artirir.
        mail.setFrom(kurum + " <" + from + ">");
        mail.setReplyTo(from);
        mail.setSubject(kurum + " — ödeme hatırlatması");
        mail.setText("""
                Sayın Veli,

                %s öğrencimizin birikmiş ödeme bakiyesi %s TL'dir.

                Ödemenizi kurumumuza iletebilir, bir yanlışlık olduğunu düşünüyorsanız
                bizimle iletişime geçebilirsiniz.

                İlginiz için teşekkür ederiz.

                %s

                ---
                Bu hatırlatma, kurumun öğrenci takip sistemi (Artademi) üzerinden gönderilmiştir.
                """.formatted(aday.adSoyad(), aday.bakiye().toPlainString(), kurum));
        mailSender.send(mail);
    }

    private List<StudentBalanceRow> borcluSatirlar() {
        // Sayfalama raporun sozlesmesi; hatirlatma icin makul bir ust sinirla hepsini alalim.
        return reportService.studentBalances(true, PageRequest.of(0, 500)).getContent();
    }

    private static Instant sogumaSiniri() {
        return Instant.now().minus(Duration.ofDays(SOGUMA_GUN));
    }

    private static String kullaniciAdi() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            if (u != null && !u.isBlank()) {
                return u;
            }
        }
        return "sistem";
    }
}
