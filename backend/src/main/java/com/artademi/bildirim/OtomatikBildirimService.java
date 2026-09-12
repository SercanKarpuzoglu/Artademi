package com.artademi.bildirim;

import com.artademi.attendance.AttendanceEntry;
import com.artademi.attendance.AttendanceEntryRepository;
import com.artademi.platform.TenantService;
import com.artademi.report.ReportService;
import com.artademi.report.dto.FinancialSummaryResponse;
import com.artademi.user.UserService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artademi.schedule.Schedule;
import com.artademi.schedule.ScheduleRepository;
import com.artademi.schedule.HaftaGunu;
import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.group.Group;
import com.artademi.teacher.Teacher;
import com.artademi.bildirim.kanal.EpostaKanali;

/**
 * Otomatik bildirimler: devamsizlik ve haftalik ozet.
 *
 * <p>⚠️ Bu servisin metodlari {@code TenantContext} AKTIF iken cagrilir — bagami
 * {@link BildirimScheduler} kurar. Kendisi tenant cozmez.
 *
 * <p>Borc hatirlatmasi burada DEGIL, {@code BorcHatirlatmaService.otomatikGonder()}
 * icindedir: soguma/tavan kurallari ve mail metni zaten orada yasiyor, kopyalamak iki
 * yerin ayrisip tutarsizlasmasi demek olurdu.
 */
@Service
public class OtomatikBildirimService {

    private static final Logger log = LoggerFactory.getLogger(OtomatikBildirimService.class);

    /** Tek gunde gonderilecek azami devamsizlik bildirimi (alan adi itibari korumasi). */
    static final int DEVAMSIZLIK_TAVAN = 200;

    private final AttendanceEntryRepository entries;
    private final DevamsizlikBildirimiRepository izler;
    private final ReportService reportService;
    private final TenantService tenantService;
    private final UserService users;
    private final JavaMailSender mailSender;
    private final String from;
    private final String smtpUsername;

    private final ScheduleRepository schedules;
    private final AttendanceSessionRepository sessions;
    private final UygulamaBildirimService uygulamaBildirimi;
    private final EpostaKanali eposta;

    public OtomatikBildirimService(AttendanceEntryRepository entries,
            DevamsizlikBildirimiRepository izler, ReportService reportService,
            TenantService tenantService, UserService users, JavaMailSender mailSender,
            @Value("${artademi.mail.from}") String from,
            @Value("${spring.mail.username:}") String smtpUsername,
            ScheduleRepository schedules, AttendanceSessionRepository sessions,
            UygulamaBildirimService uygulamaBildirimi, EpostaKanali eposta) {
        this.entries = entries;
        this.izler = izler;
        this.reportService = reportService;
        this.tenantService = tenantService;
        this.users = users;
        this.mailSender = mailSender;
        this.from = from;
        this.smtpUsername = smtpUsername;
        this.schedules = schedules;
        this.sessions = sessions;
        this.uygulamaBildirimi = uygulamaBildirimi;
        this.eposta = eposta;
    }

    /**
     * O gun derse gelmeyen ogrencilerin velilerine bilgi maili.
     *
     * <p>Mukerrer kalkani: ayni (ogrenci, oturum) icin daha once bildirim gonderilmisse
     * ATLANIR — job tekrar calissa da veli iki kez uyarilmaz.
     *
     * @return gonderilen bildirim sayisi
     */
    @Transactional
    public int devamsizlikBildirimleri(LocalDate gun) {
        if (smtpYok()) {
            return 0;
        }
        List<AttendanceEntry> gelmeyenler = entries.gelmeyenler(gun);
        if (gelmeyenler.isEmpty()) {
            return 0;
        }

        List<Long> oturumIdler = gelmeyenler.stream()
                .map(e -> e.getSession().getId()).distinct().toList();
        Set<String> zatenGonderilmis = new HashSet<>();
        izler.gonderilmisCiftler(oturumIdler)
                .forEach(c -> zatenGonderilmis.add(c[0] + "#" + c[1]));

        String kurum = tenantService.currentName();
        int gonderilen = 0;
        for (AttendanceEntry e : gelmeyenler) {
            if (gonderilen >= DEVAMSIZLIK_TAVAN) {
                log.warn("Devamsızlık bildirimi günlük tavana ({}) ulaştı", DEVAMSIZLIK_TAVAN);
                break;
            }
            Long ogrenciId = e.getOgrenci().getId();
            Long oturumId = e.getSession().getId();
            if (zatenGonderilmis.contains(ogrenciId + "#" + oturumId)) {
                continue;
            }
            String alici = e.getOgrenci().getVeliMail();
            if (alici == null || alici.isBlank()) {
                continue;
            }
            try {
                mailSender.send(devamsizlikMaili(e, kurum, alici, gun));
                izler.save(DevamsizlikBildirimi.of(ogrenciId, oturumId, alici));
                gonderilen++;
            } catch (RuntimeException ex) {
                // Tek mail patlarsa digerleri gitmeye DEVAM etmeli.
                log.error("Devamsızlık bildirimi gönderilemedi (ogrenci={}): {}",
                        ogrenciId, ex.getMessage());
            }
        }
        return gonderilen;
    }

    /**
     * Kurum yoneticilerine haftalik finansal ozet.
     *
     * <p>Icinde bulunulan AYIN ozeti gonderilir: hafta bazli ayri bir rapor yok ve uydurmak
     * yerine mevcut, test edilmis {@code financialSummary} kullanilir — yonetici zaten aylik
     * rakamla calisiyor.
     *
     * @return mail gonderildiyse true
     */
    @Transactional(readOnly = true)
    public boolean haftalikOzet() {
        if (smtpYok()) {
            return false;
        }
        List<String> alicilar = adminAdresleri();
        if (alicilar.isEmpty()) {
            log.warn("Haftalık özet gönderilemedi: e-postalı admin yok");
            return false;
        }
        String donem = YearMonth.now().toString();
        FinancialSummaryResponse ozet = reportService.financialSummary(donem);
        try {
            mailSender.send(ozetMaili(alicilar, tenantService.currentName(), ozet));
            return true;
        } catch (RuntimeException e) {
            log.error("Haftalık özet gönderilemedi: {}", e.getMessage());
            return false;
        }
    }

    private SimpleMailMessage devamsizlikMaili(AttendanceEntry e, String kurum, String alici,
            LocalDate gun) {
        String grup = e.getSession().getGrup() != null ? e.getSession().getGrup().getAd() : "dersi";
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(alici);
        // Gonderen ADI kurum: veli "Artademi"yi degil KENDI OKULUNU tanir.
        mail.setFrom(kurum + " <" + from + ">");
        mail.setReplyTo(from);
        mail.setSubject(kurum + " — devamsızlık bilgisi");
        mail.setText("""
                Sayın Veli,

                %s öğrencimiz %s tarihli %s dersine katılmamıştır.

                Bir yanlışlık olduğunu düşünüyorsanız bizimle iletişime geçebilirsiniz.

                %s

                ---
                Bu bilgilendirme, kurumun öğrenci takip sistemi (Artademi) üzerinden gönderilmiştir.
                """.formatted(
                e.getOgrenci().getAd() + " " + e.getOgrenci().getSoyad(),
                gun.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                grup,
                kurum));
        return mail;
    }

    private SimpleMailMessage ozetMaili(List<String> alicilar, String kurum,
            FinancialSummaryResponse o) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(alicilar.toArray(String[]::new));
        mail.setFrom(kurum + " <" + from + ">");
        mail.setReplyTo(from);
        mail.setSubject(kurum + " — haftalık özet (" + o.donem() + ")");
        mail.setText("""
                %s haftalık özeti — %s dönemi

                GELİR
                  Tahsilat      : %s TL
                  Ürün satış    : %s TL
                  Toplam        : %s TL

                GİDER
                  Ofis gideri   : %s TL
                  Hakediş       : %s TL
                  Toplam        : %s TL

                NET             : %s TL

                Ayrıntı için panele girin: Raporlar → Finansal Özet

                ---
                Bu özet otomatik gönderildi. Bildirim tercihlerinizi panelden değiştirebilirsiniz.
                """.formatted(
                kurum, o.donem(),
                o.gelir().tahsilat().toPlainString(),
                o.gelir().urunSatis().toPlainString(),
                o.gelir().toplamGelir().toPlainString(),
                o.gider().ofisGideri().toPlainString(),
                o.gider().hakedis().toPlainString(),
                o.gider().toplamGider().toPlainString(),
                o.net().toPlainString()));
        return mail;
    }

    /**
     * "Yoklama alinmadi" (Dalga C): o gunun aktif ders saatlerinden oturumu ACILMAMIS olanlar icin
     * egitmene uygulama ici bildirim (+ tercihe bagli e-posta) ve ofise bilgi. Gunde bir kez calisir;
     * ayni ders icin ikinci bildirim yalniz job tekrar tetiklenirse olusur.
     *
     * @return bildirim uretilen ders saati sayisi
     */
    @Transactional
    public int yoklamaAlinmadi(LocalDate gun, boolean epostaGonder) {
        HaftaGunu haftaGunu = HaftaGunu.values()[gun.getDayOfWeek().getValue() - 1];
        String kurum = tenantService.currentName();
        int n = 0;
        for (Schedule s : schedules.findAktifByGun(haftaGunu)) {
            Group g = s.getGrup();
            if (g == null || sessions.existsByGrupAndTarih(g.getId(), gun)) {
                continue;
            }
            String saat = s.getBaslangicSaati() + "–" + s.getBitisSaati();
            Teacher egitmen = g.getOgretmen();
            String egitmenAd = egitmen == null ? "Eğitmen" : egitmen.getAd() + " " + egitmen.getSoyad();
            String baglanti = "/yoklama";
            if (egitmen != null && egitmen.getKeycloakUserId() != null && !egitmen.getKeycloakUserId().isBlank()) {
                uygulamaBildirimi.gonder(UygulamaBildirimTipi.YOKLAMA_ALINMADI, "TEACHER",
                        egitmen.getKeycloakUserId(), g.getAd() + " dersinin yoklaması alınmadı",
                        gun + " " + saat + " — yoklamayı şimdi alabilirsiniz", baglanti);
            }
            uygulamaBildirimi.gonder(UygulamaBildirimTipi.YOKLAMA_ALINMADI, UygulamaBildirimService.OFIS, null,
                    egitmenAd + " — " + g.getAd() + " yoklaması alınmadı", gun + " " + saat, baglanti);
            if (epostaGonder && egitmen != null) {
                eposta.gonder(egitmen.getEmail(), "[" + kurum + "] " + g.getAd() + " yoklaması alınmadı",
                        "Merhaba " + egitmenAd + ",\n\n" + gun + " " + saat + " " + g.getAd()
                                + " dersinin yoklaması alınmadı. Panelden alabilirsiniz.\n\n" + kurum);
            }
            n++;
        }
        return n;
    }

    private List<String> adminAdresleri() {
        return users.list(true, "ADMIN", null, 0, 50).icerik().stream()
                .map(u -> u.email())
                .filter(e -> e != null && !e.isBlank())
                .toList();
    }

    private boolean smtpYok() {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("Otomatik bildirim atlandı: SMTP yapılandırılmamış");
            return true;
        }
        return false;
    }
}
