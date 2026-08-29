package com.artademi.export;

import com.artademi.attendance.AttendanceEntryRepository;
import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.branch.BranchRepository;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.ExpenseRepository;
import com.artademi.finance.PaymentRepository;
import com.artademi.group.GroupRepository;
import com.artademi.inventory.ProductRepository;
import com.artademi.inventory.SaleRepository;
import com.artademi.payout.PayoutRepository;
import com.artademi.platform.TenantService;
import com.artademi.room.RoomRepository;
import com.artademi.schedule.ScheduleRepository;
import com.artademi.student.StudentRepository;
import com.artademi.teacher.TeacherRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kurumun TUM verisini ZIP icinde CSV olarak disa aktarir (KVKK veri tasinabilirligi).
 *
 * <p><b>Neden var:</b> mesafeli satis sozlesmemiz ve KVKK metnimiz "verileriniz talebiniz halinde
 * disa aktarilabilir" diye taahhut veriyor; karsiligi yoktu. Ayrica kurum aboneligini
 * sonlandirirken verisini yaninda goturebilmeli — veriyi rehin almak dogru degil.
 *
 * <p><b>Izolasyon:</b> tum repository'ler TenantAware entity'ler uzerinde calisir → Hibernate
 * tenant filtresi otomatik uygular. Baska kurumun verisi teknik olarak bu dosyaya GIREMEZ.
 *
 * <p>Iliskiler okunur hale cevrilir (ogrenci nesnesi degil "Ada Yılmaz"), cunku dosyayi acacak
 * kisi Excel kullanan bir okul yoneticisidir, gelistirici degil.
 */
@Service
public class VeriDisaAktarmaService {

    private final TenantService tenantService;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final BranchRepository branches;
    private final RoomRepository rooms;
    private final GroupRepository groups;
    private final EnrollmentRepository enrollments;
    private final ScheduleRepository schedules;
    private final AttendanceSessionRepository sessions;
    private final AttendanceEntryRepository entries;
    private final AccrualRepository accruals;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final PayoutRepository payouts;
    private final ProductRepository products;
    private final SaleRepository sales;

    public VeriDisaAktarmaService(TenantService tenantService, StudentRepository students,
            TeacherRepository teachers, BranchRepository branches, RoomRepository rooms,
            GroupRepository groups, EnrollmentRepository enrollments, ScheduleRepository schedules,
            AttendanceSessionRepository sessions, AttendanceEntryRepository entries,
            AccrualRepository accruals, PaymentRepository payments, ExpenseRepository expenses,
            PayoutRepository payouts, ProductRepository products, SaleRepository sales) {
        this.tenantService = tenantService;
        this.students = students;
        this.teachers = teachers;
        this.branches = branches;
        this.rooms = rooms;
        this.groups = groups;
        this.enrollments = enrollments;
        this.schedules = schedules;
        this.sessions = sessions;
        this.entries = entries;
        this.accruals = accruals;
        this.payments = payments;
        this.expenses = expenses;
        this.payouts = payouts;
        this.products = products;
        this.sales = sales;
    }

    /** Dosya adi: kurum-adi_tarih.zip (indirilince ne oldugu belli olsun). */
    public String dosyaAdi() {
        String kurum = tenantService.currentName();
        String temiz = (kurum == null ? "kurum" : kurum)
                .replaceAll("[^A-Za-z0-9ğüşıöçĞÜŞİÖÇ ]", "").trim().replace(' ', '-');
        return temiz.toLowerCase() + "_veri_" + LocalDate.now() + ".zip";
    }

    @Transactional(readOnly = true)
    public byte[] zipUret() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            yaz(zip, "00-OKUBENI.txt", okuBeni().getBytes(StandardCharsets.UTF_8));
            yaz(zip, "01-ogrenciler.csv", ogrenciler());
            yaz(zip, "02-ogretmenler.csv", ogretmenler());
            yaz(zip, "03-branslar.csv", branslar());
            yaz(zip, "04-salonlar.csv", salonlar());
            yaz(zip, "05-gruplar.csv", gruplar());
            yaz(zip, "06-kayitlar.csv", kayitlar());
            yaz(zip, "07-program.csv", program());
            yaz(zip, "08-yoklama-oturumlari.csv", yoklamaOturumlari());
            yaz(zip, "09-yoklama-girisleri.csv", yoklamaGirisleri());
            yaz(zip, "10-tahakkuklar.csv", tahakkuklar());
            yaz(zip, "11-tahsilatlar.csv", tahsilatlar());
            yaz(zip, "12-giderler.csv", giderler());
            yaz(zip, "13-hakedisler.csv", hakedisler());
            yaz(zip, "14-urunler.csv", urunler());
            yaz(zip, "15-satislar.csv", satislar());
        }
        return out.toByteArray();
    }

    private static void yaz(ZipOutputStream zip, String ad, byte[] icerik) throws IOException {
        zip.putNextEntry(new ZipEntry(ad));
        zip.write(icerik);
        zip.closeEntry();
    }

    private String okuBeni() {
        return """
                ARTADEMI — KURUM VERİ DIŞA AKTARIMI
                Kurum : %s
                Tarih : %s

                Bu arşiv, kurumunuza ait tüm kayıtların dışa aktarılmış halidir.
                Dosyalar CSV biçimindedir ve Excel ile doğrudan açılabilir
                (UTF-8 + noktalı virgül ayracı — Türkçe karakterler bozulmaz).

                İçerik:
                  01 Öğrenciler          09 Yoklama girişleri
                  02 Öğretmenler         10 Tahakkuklar
                  03 Branşlar            11 Tahsilatlar
                  04 Salonlar            12 Giderler
                  05 Gruplar             13 Hakedişler
                  06 Kayıtlar            14 Ürünler
                  07 Program             15 Satışlar
                  08 Yoklama oturumları

                Not: Bu dosya kişisel veri içerir (öğrenci ve veli bilgileri).
                KVKK kapsamında güvenli saklamak ve gereksiz paylaşmamak
                kurumunuzun sorumluluğundadır.

                Artademi — info@artademi.com
                """.formatted(tenantService.currentName(), LocalDate.now());
    }

    private byte[] ogrenciler() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Soyad", "TC Kimlik No", "Doğum Tarihi", "Telefon", "Yetişkin mi",
                "Durum", "Anne Ad", "Anne TC", "Anne Telefon", "Baba Ad", "Baba TC",
                "Baba Telefon", "Veli Meslek", "Ev Adresi", "Veli E-posta");
        students.findAll().forEach(s -> c.satir(s.getId(), s.getAd(), s.getSoyad(),
                s.getTcKimlikNo(), s.getDogumTarihi(), s.getTelefon(), s.isYetiskinMi() ? "Evet" : "Hayır",
                s.getStatus(), s.getAnneAd(), s.getAnneTcKimlikNo(), s.getAnneTelefon(),
                s.getBabaAd(), s.getBabaTcKimlikNo(), s.getBabaTelefon(), s.getVeliMeslek(),
                s.getEvAdresi(), s.getVeliMail()));
        return c.baytlar();
    }

    private byte[] ogretmenler() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Soyad", "Telefon", "E-posta", "Aktif");
        teachers.findAll().forEach(t -> c.satir(t.getId(), t.getAd(), t.getSoyad(),
                t.getTelefon(), t.getEmail(), t.isAktif() ? "Evet" : "Hayır"));
        return c.baytlar();
    }

    private byte[] branslar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Açıklama", "Aktif");
        branches.findAll().forEach(b -> c.satir(b.getId(), b.getAd(), b.getAciklama(),
                b.isAktif() ? "Evet" : "Hayır"));
        return c.baytlar();
    }

    private byte[] salonlar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Kapasite", "Açıklama", "Aktif");
        rooms.findAll().forEach(r -> c.satir(r.getId(), r.getAd(), r.getKapasite(),
                r.getAciklama(), r.isAktif() ? "Evet" : "Hayır"));
        return c.baytlar();
    }

    private byte[] gruplar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Tip", "Branş", "Öğretmen", "Salon", "Seviye", "Aylık Aidat",
                "Ders Başı Ücret", "Hakediş Tipi");
        groups.findAll().forEach(g -> c.satir(g.getId(), g.getAd(), g.getTip(),
                g.getBrans() == null ? null : g.getBrans().getAd(),
                adSoyad(g.getOgretmen() == null ? null : g.getOgretmen().getAd(),
                        g.getOgretmen() == null ? null : g.getOgretmen().getSoyad()),
                g.getSalon() == null ? null : g.getSalon().getAd(),
                g.getSeviye(), g.getAylikAidat(), g.getDersBasiUcret(), g.getHakedisTipi()));
        return c.baytlar();
    }

    private byte[] kayitlar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Öğrenci", "Grup", "Kayıt Tarihi", "Durum", "Ayrılma Tarihi");
        enrollments.findAll().forEach(e -> c.satir(e.getId(),
                adSoyad(e.getOgrenci().getAd(), e.getOgrenci().getSoyad()),
                e.getGrup().getAd(), e.getKayitTarihi(), e.getDurum(), e.getAyrilmaTarihi()));
        return c.baytlar();
    }

    private byte[] program() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Grup", "Gün", "Başlangıç", "Bitiş", "Aktif");
        schedules.findAll().forEach(s -> c.satir(s.getId(), s.getGrup().getAd(), s.getGun(),
                s.getBaslangicSaati(), s.getBitisSaati(), s.isAktif() ? "Evet" : "Hayır"));
        return c.baytlar();
    }

    private byte[] yoklamaOturumlari() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Grup", "Tarih", "Not");
        sessions.findAll().forEach(s -> c.satir(s.getId(), s.getGrup().getAd(), s.getTarih(),
                s.getNotu()));
        return c.baytlar();
    }

    private byte[] yoklamaGirisleri() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Oturum Id", "Tarih", "Grup", "Öğrenci", "Durum");
        entries.findAll().forEach(e -> c.satir(e.getId(), e.getSession().getId(),
                e.getSession().getTarih(), e.getSession().getGrup().getAd(),
                adSoyad(e.getOgrenci().getAd(), e.getOgrenci().getSoyad()), e.getDurum()));
        return c.baytlar();
    }

    private byte[] tahakkuklar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Öğrenci", "Grup", "Dönem", "Tutar", "Açıklama");
        accruals.findAll().forEach(a -> c.satir(a.getId(),
                adSoyad(a.getOgrenci().getAd(), a.getOgrenci().getSoyad()),
                a.getGrup() == null ? null : a.getGrup().getAd(),
                a.getDonem(), a.getTutar(), a.getAciklama()));
        return c.baytlar();
    }

    private byte[] tahsilatlar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Öğrenci", "Grup", "Tutar", "Ödeme Tarihi", "Yöntem", "Açıklama");
        payments.findAll().forEach(p -> c.satir(p.getId(),
                adSoyad(p.getOgrenci().getAd(), p.getOgrenci().getSoyad()),
                p.getGrup() == null ? null : p.getGrup().getAd(),
                p.getTutar(), p.getOdemeTarihi(), p.getOdemeYontemi(), p.getAciklama()));
        return c.baytlar();
    }

    private byte[] giderler() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Tutar", "Gider Tarihi", "Kategori", "Açıklama");
        expenses.findAll().forEach(g -> c.satir(g.getId(), g.getTutar(), g.getGiderTarihi(),
                g.getKategori(), g.getAciklama()));
        return c.baytlar();
    }

    private byte[] hakedisler() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Öğretmen", "Dönem", "Hakediş Tipi", "Tutar", "Ders Sayısı", "Birim Ücret",
                "Toplam Tahsilat", "KDV Oranı", "Net Ciro", "Oran", "Durum", "Ödeme Tarihi");
        payouts.findAll().forEach(p -> c.satir(p.getId(),
                adSoyad(p.getOgretmen().getAd(), p.getOgretmen().getSoyad()),
                p.getDonem(), p.getHakedisTipi(), p.getHesaplananTutar(), p.getDersSayisi(),
                p.getBirimUcret(), p.getToplamTahsilat(), p.getKdvOrani(), p.getNetCiro(),
                p.getOran(), p.getDurum(), p.getOdemeTarihi()));
        return c.baytlar();
    }

    private byte[] urunler() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ad", "Satış Fiyatı", "Stok Adedi", "Açıklama", "Aktif");
        products.findAll().forEach(p -> c.satir(p.getId(), p.getAd(), p.getSatisFiyati(),
                p.getStokAdedi(), p.getAciklama(), p.isAktif() ? "Evet" : "Hayır"));
        return c.baytlar();
    }

    private byte[] satislar() throws IOException {
        CsvYazici c = new CsvYazici();
        c.satir("Id", "Ürün", "Öğrenci", "Adet", "Birim Fiyat", "Toplam", "Satış Tarihi",
                "Açıklama");
        sales.findAll().forEach(s -> c.satir(s.getId(),
                s.getUrun() == null ? null : s.getUrun().getAd(),
                s.getOgrenci() == null ? null
                        : adSoyad(s.getOgrenci().getAd(), s.getOgrenci().getSoyad()),
                s.getAdet(), s.getBirimFiyat(), s.getToplamTutar(), s.getSatisTarihi(),
                s.getAciklama()));
        return c.baytlar();
    }

    private static String adSoyad(String ad, String soyad) {
        if (ad == null && soyad == null) {
            return null;
        }
        return ((ad == null ? "" : ad) + " " + (soyad == null ? "" : soyad)).trim();
    }
}
