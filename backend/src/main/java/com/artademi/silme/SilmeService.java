package com.artademi.silme;

import com.artademi.attendance.AttendanceEntry;
import com.artademi.attendance.AttendanceEntryRepository;
import com.artademi.attendance.AttendanceSession;
import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.basvuru.Basvuru;
import com.artademi.basvuru.BasvuruRepository;
import com.artademi.branch.Branch;
import com.artademi.branch.BranchRepository;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.silme.SoftDeletable;
import com.artademi.common.tenant.TenantContext;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentDurumu;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.Accrual;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.Expense;
import com.artademi.finance.ExpenseRepository;
import com.artademi.finance.Payment;
import com.artademi.finance.PaymentRepository;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.inventory.Product;
import com.artademi.inventory.ProductRepository;
import com.artademi.inventory.Sale;
import com.artademi.inventory.SaleRepository;
import com.artademi.kasa.Kasa;
import com.artademi.kasa.KasaHareketiRepository;
import com.artademi.kasa.KasaRepository;
import com.artademi.paket.DersPaketi;
import com.artademi.paket.DersPaketiRepository;
import com.artademi.paket.PaketKullanimRepository;
import com.artademi.room.Room;
import com.artademi.room.RoomRepository;
import com.artademi.schedule.Schedule;
import com.artademi.schedule.ScheduleRepository;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.sube.Sube;
import com.artademi.sube.SubeRepository;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherRepository;
import com.artademi.tedarikci.Tedarikci;
import com.artademi.tedarikci.TedarikciRepository;
import com.artademi.telafi.TelafiHakki;
import com.artademi.telafi.TelafiHakkiRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yumusak silme merkezi (Dalga B-3, urun karari 2026-09-10: "Sil her sayfada, yalniz yonetici, uyari ile,
 * geri alinabilir"). Tek serviste toplandi: kurallar bir yerde, her modulde ayri DELETE ucu yok.
 *
 * <p>Uc adim: {@link #onizle} (modal icin: engel / etkiler / bagli kayitlar), {@link #sil} (damgala +
 * yan etkiler), {@link #geriAl} (native UPDATE — silinen satir @SQLRestriction yuzunden JPQL ile
 * bulunamaz, bu yuzden SQL; tenant_id kosulu ELLE yazilir cunku native sorgu Hibernate filtresine tabi
 * DEGILDIR).
 *
 * <p>Kurallar (bkz. HANDOFF §7.29): tanim kayitlari (egitmen, salon, brans, sube, kasa) hala kullanan
 * aktif kayit varsa SILINEMEZ (409 SILINEMEZ); ogrenci/grup silinince aktif kayitlar AYRILDI olur; para
 * kayitlari (odeme, gider) serbest; tahakkuk odemesi varsa, paket kullanimi varsa, oturumdan paket
 * dusulmus/telafi hakki dogmussa silinemez; satis silinince stok geri eklenir, geri alininca dusulur.
 */
@Service
public class SilmeService {

    private final EntityManager em;
    private final StudentRepository students;
    private final GroupRepository groups;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final BranchRepository branches;
    private final SubeRepository subeler;
    private final ProductRepository products;
    private final TedarikciRepository tedarikciler;
    private final KasaRepository kasalar;
    private final KasaHareketiRepository kasaHareketleri;
    private final AccrualRepository accruals;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final SaleRepository sales;
    private final DersPaketiRepository paketler;
    private final PaketKullanimRepository paketKullanimlari;
    private final TelafiHakkiRepository telafiler;
    private final BasvuruRepository basvurular;
    private final ScheduleRepository schedules;
    private final AttendanceSessionRepository sessions;
    private final AttendanceEntryRepository entries;
    private final EnrollmentRepository enrollments;

    public SilmeService(EntityManager em, StudentRepository students, GroupRepository groups,
            TeacherRepository teachers, RoomRepository rooms, BranchRepository branches,
            SubeRepository subeler, ProductRepository products, TedarikciRepository tedarikciler,
            KasaRepository kasalar, KasaHareketiRepository kasaHareketleri, AccrualRepository accruals,
            PaymentRepository payments, ExpenseRepository expenses, SaleRepository sales,
            DersPaketiRepository paketler, PaketKullanimRepository paketKullanimlari,
            TelafiHakkiRepository telafiler, BasvuruRepository basvurular, ScheduleRepository schedules,
            AttendanceSessionRepository sessions, AttendanceEntryRepository entries,
            EnrollmentRepository enrollments) {
        this.em = em;
        this.students = students;
        this.groups = groups;
        this.teachers = teachers;
        this.rooms = rooms;
        this.branches = branches;
        this.subeler = subeler;
        this.products = products;
        this.tedarikciler = tedarikciler;
        this.kasalar = kasalar;
        this.kasaHareketleri = kasaHareketleri;
        this.accruals = accruals;
        this.payments = payments;
        this.expenses = expenses;
        this.sales = sales;
        this.paketler = paketler;
        this.paketKullanimlari = paketKullanimlari;
        this.telafiler = telafiler;
        this.basvurular = basvurular;
        this.schedules = schedules;
        this.sessions = sessions;
        this.entries = entries;
        this.enrollments = enrollments;
    }

    // ---------- onizleme ----------

    @Transactional(readOnly = true)
    public SilmeOnizleme onizle(SilinebilirTur tur, Long id) {
        return hesapla(tur, id);
    }

    /** Ortak cekirdek: entity'yi bul, kurallari degerlendir. sil() de ayni sonucu kullanir. */
    private SilmeOnizleme hesapla(SilinebilirTur tur, Long id) {
        List<String> etkiler = new ArrayList<>();
        List<SilmeOnizleme.BagliKayit> bagli = new ArrayList<>();
        String engel = null;
        String ad;
        switch (tur) {
            case OGRENCI -> {
                Student s = bul(tur, students.findScopedById(id));
                ad = s.getAd() + " " + s.getSoyad();
                long aktifKayit = enrollments.findAktifByOgrenciIds(List.of(id)).size();
                if (aktifKayit > 0) {
                    etkiler.add(aktifKayit + " aktif grup kaydı AYRILDI olacak");
                }
                bagliEkle(bagli, "Ödeme", payments.countByOgrenci(id));
                bagliEkle(bagli, "Tahakkuk", accruals.countByOgrenci(id));
                bagliEkle(bagli, "Ürün satışı", sales.countByOgrenci(id));
                bagliEkle(bagli, "Ders paketi", paketler.countByOgrenci(id));
                bagliEkle(bagli, "Telafi hakkı", telafiler.countByOgrenci(id));
                bagliEkle(bagli, "Yoklama kaydı", entries.countByOgrenci(id));
                if (!bagli.isEmpty()) {
                    etkiler.add("Bağlı kayıtlar silinmez; para izi ve yoklama geçmişi korunur, öğrenci listelerden kalkar");
                }
            }
            case GRUP -> {
                Group g = bul(tur, groups.findScopedById(id));
                ad = g.getAd();
                long aktifKayit = enrollments.findAktifByGrup(id).size();
                if (aktifKayit > 0) {
                    etkiler.add(aktifKayit + " aktif öğrenci kaydı AYRILDI olacak");
                }
                long dersSaati = schedules.findByGrupId(id).size();
                if (dersSaati > 0) {
                    etkiler.add(dersSaati + " ders saati silinecek");
                }
                bagliEkle(bagli, "Yoklama oturumu", sessions.countByGrup(id));
                bagliEkle(bagli, "Ödeme", payments.countByGrup(id));
                bagliEkle(bagli, "Tahakkuk", accruals.countByGrup(id));
            }
            case EGITMEN -> {
                Teacher t = bul(tur, teachers.findScopedById(id));
                ad = t.getAd() + " " + t.getSoyad();
                long grup = groups.countByOgretmen(id);
                if (grup > 0) {
                    engel = grup + " grup bu eğitmene atanmış; önce grupların eğitmenini değiştirin";
                }
            }
            case SALON -> {
                Room r = bul(tur, rooms.findScopedById(id));
                ad = r.getAd();
                long grup = groups.countBySalon(id);
                if (grup > 0) {
                    engel = grup + " grup bu salonu kullanıyor; önce grupların salonunu değiştirin";
                }
            }
            case BRANS -> {
                Branch b = bul(tur, branches.findScopedById(id));
                ad = b.getAd();
                long grup = groups.countByBrans(id);
                if (grup > 0) {
                    engel = grup + " grup bu branşta; önce grupları taşıyın veya silin";
                }
            }
            case SUBE -> {
                Sube s = bul(tur, subeler.findScopedById(id));
                ad = s.getAd();
                long grup = groups.countBySube(id);
                long salon = rooms.countBySube(id);
                if (grup + salon > 0) {
                    engel = grup + " grup ve " + salon + " salon bu şubeye bağlı; önce onları taşıyın";
                }
            }
            case URUN -> {
                Product p = bul(tur, products.findScopedById(id));
                ad = p.getAd();
                bagliEkle(bagli, "Satış", sales.countByUrun(id));
            }
            case TEDARIKCI -> {
                Tedarikci t = bul(tur, tedarikciler.findScopedById(id));
                ad = t.getAd();
                bagliEkle(bagli, "Gider", expenses.countByTedarikci(id));
            }
            case KASA -> {
                Kasa k = bul(tur, kasalar.findScopedById(id));
                ad = k.getAd();
                long hareket = kasaHareketleri.countByKasa(id) + payments.countByKasa(id) + expenses.countByKasa(id);
                if (hareket > 0) {
                    engel = "Kasada " + hareket + " hareket var; bakiye izi bozulmasın diye silinemez, pasifleştirin";
                }
            }
            case TAHAKKUK -> {
                Accrual a = bul(tur, accruals.findScopedById(id));
                ad = "Tahakkuk " + a.getDonem() + " · " + a.getTutar() + " ₺";
                long odeme = payments.countByAccrual(id);
                if (odeme > 0) {
                    engel = "Bu tahakkuka bağlı " + odeme + " ödeme var; önce ödemeleri silin";
                }
                etkiler.add("Öğrenci bakiyesi yeniden hesaplanır");
            }
            case ODEME -> {
                Payment p = bul(tur, payments.findScopedById(id));
                ad = "Ödeme " + p.getOdemeTarihi() + " · " + p.getTutar() + " ₺";
                etkiler.add("Öğrenci bakiyesi ve kasa bakiyesi yeniden hesaplanır");
            }
            case GIDER -> {
                Expense x = bul(tur, expenses.findScopedById(id));
                ad = "Gider " + x.getGiderTarihi() + " · " + x.getTutar() + " ₺";
                etkiler.add("Kasa bakiyesi yeniden hesaplanır");
            }
            case SATIS -> {
                Sale s = bul(tur, sales.findScopedById(id));
                ad = "Satış " + s.getSatisTarihi() + " · " + s.getToplamTutar() + " ₺";
                etkiler.add(s.getAdet() + " adet stoğa geri eklenir");
            }
            case PAKET -> {
                DersPaketi d = bul(tur, paketler.findScopedById(id));
                ad = d.getAd();
                long kullanim = paketKullanimlari.countByPaket(id);
                if (kullanim > 0) {
                    engel = "Paketten " + kullanim + " ders kullanılmış; silinemez";
                }
            }
            case TELAFI -> {
                TelafiHakki t = bul(tur, telafiler.findScopedById(id));
                ad = "Telafi hakkı " + t.getVerilmeTarihi();
            }
            case BASVURU -> {
                Basvuru b = bul(tur, basvurular.findScopedById(id));
                ad = b.getAd() + " " + b.getSoyad();
            }
            case DERS_SAATI -> {
                Schedule s = bul(tur, schedules.findScopedById(id));
                ad = s.getGun() + " " + s.getBaslangicSaati() + "–" + s.getBitisSaati();
            }
            case YOKLAMA_OTURUMU -> {
                AttendanceSession s = bul(tur, sessions.findScopedById(id));
                ad = "Yoklama " + s.getTarih() + (s.getGrup() != null ? " · " + s.getGrup().getAd() : "");
                long kullanim = paketKullanimlari.countByOturum(id);
                long telafi = telafiler.countByKaynakOturum(id);
                if (kullanim > 0) {
                    engel = "Bu oturumdan " + kullanim + " ders paketi kontörü düşülmüş; önce yoklamayı İzinli yapın";
                } else if (telafi > 0) {
                    engel = "Bu oturumdan " + telafi + " telafi hakkı doğmuş; önce telafi hakkını iptal edin";
                }
                long satir = entries.findBySessionId(id).size();
                if (satir > 0) {
                    etkiler.add(satir + " yoklama satırı silinecek");
                }
            }
            default -> throw new NotFoundException("Bilinmeyen kayıt türü");
        }
        return new SilmeOnizleme(tur.yol(), id, ad, engel == null, engel, etkiler, bagli);
    }

    // ---------- silme ----------

    @Transactional
    public void sil(SilinebilirTur tur, Long id) {
        SilmeOnizleme on = hesapla(tur, id);
        if (!on.silinebilir()) {
            throw new ConflictException(on.engel(), "SILINEMEZ");
        }
        String kim = kullaniciAdi();
        switch (tur) {
            case OGRENCI -> {
                for (Enrollment e : enrollments.findAktifByOgrenciIds(List.of(id))) {
                    ayril(e);
                }
                damgala(students.findScopedById(id), kim);
            }
            case GRUP -> {
                for (Enrollment e : enrollments.findAktifByGrup(id)) {
                    ayril(e);
                }
                for (Schedule s : schedules.findByGrupId(id)) {
                    s.sil(kim);
                }
                damgala(groups.findScopedById(id), kim);
            }
            case EGITMEN -> damgala(teachers.findScopedById(id), kim);
            case SALON -> damgala(rooms.findScopedById(id), kim);
            case BRANS -> damgala(branches.findScopedById(id), kim);
            case SUBE -> damgala(subeler.findScopedById(id), kim);
            case URUN -> damgala(products.findScopedById(id), kim);
            case TEDARIKCI -> damgala(tedarikciler.findScopedById(id), kim);
            case KASA -> damgala(kasalar.findScopedById(id), kim);
            case TAHAKKUK -> damgala(accruals.findScopedById(id), kim);
            case ODEME -> damgala(payments.findScopedById(id), kim);
            case GIDER -> damgala(expenses.findScopedById(id), kim);
            case SATIS -> {
                Sale s = sales.findScopedById(id).orElseThrow();
                Product p = s.getUrun();
                if (p != null) {
                    p.setStokAdedi(p.getStokAdedi() + s.getAdet());
                }
                s.sil(kim);
            }
            case PAKET -> damgala(paketler.findScopedById(id), kim);
            case TELAFI -> damgala(telafiler.findScopedById(id), kim);
            case BASVURU -> damgala(basvurular.findScopedById(id), kim);
            case DERS_SAATI -> damgala(schedules.findScopedById(id), kim);
            case YOKLAMA_OTURUMU -> {
                for (AttendanceEntry e : entries.findBySessionId(id)) {
                    e.sil(kim);
                }
                damgala(sessions.findScopedById(id), kim);
            }
            default -> throw new NotFoundException("Bilinmeyen kayıt türü");
        }
    }

    // ---------- silinenler / geri alma (native: @SQLRestriction'i asan TEK yer) ----------

    /** Silinen kayitlar, en yeni en ustte (en fazla 200). Tenant kosulu ELLE — native sorgu filtreye tabi degil. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<SilinenKayit> silinenler(SilinebilirTur tur) {
        UUID tenant = tenant();
        String sql = "SELECT id, " + tur.adIfadesi() + " AS ad, silindi_tarihi, silen FROM " + tur.tablo()
                + " WHERE tenant_id = :tenant AND silindi_tarihi IS NOT NULL ORDER BY silindi_tarihi DESC LIMIT 200";
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("tenant", tenant).getResultList();
        List<SilinenKayit> sonuc = new ArrayList<>();
        for (Object[] r : rows) {
            sonuc.add(new SilinenKayit(tur.yol(), ((Number) r[0]).longValue(), String.valueOf(r[1]),
                    zamanaCevir(r[2]), (String) r[3]));
        }
        return sonuc;
    }

    /** Geri al: damgayi kaldirir. Satis geri alininca stok tekrar duser (yetersizse 409). */
    @Transactional
    public void geriAl(SilinebilirTur tur, Long id) {
        UUID tenant = tenant();
        if (tur == SilinebilirTur.SATIS) {
            List<?> satisSatirlari = em.createNativeQuery(
                    "SELECT urun_id, adet FROM sale WHERE id = :id AND tenant_id = :tenant AND silindi_tarihi IS NOT NULL")
                    .setParameter("id", id).setParameter("tenant", tenant).getResultList();
            if (satisSatirlari.isEmpty()) {
                throw new NotFoundException("Silinmiş satış bulunamadı: " + id);
            }
            Object[] satis = (Object[]) satisSatirlari.get(0);
            long urunId = ((Number) satis[0]).longValue();
            int adet = ((Number) satis[1]).intValue();
            Product p = products.findScopedById(urunId)
                    .orElseThrow(() -> new ConflictException("Satışın ürünü silinmiş; önce ürünü geri alın", "SILINEMEZ"));
            if (p.getStokAdedi() < adet) {
                throw new ConflictException("Stok yetersiz: mevcut " + p.getStokAdedi() + ", satış " + adet, "SILINEMEZ");
            }
            p.setStokAdedi(p.getStokAdedi() - adet);
        }
        int n = em.createNativeQuery("UPDATE " + tur.tablo()
                        + " SET silindi_tarihi = NULL, silen = NULL WHERE id = :id AND tenant_id = :tenant AND silindi_tarihi IS NOT NULL")
                .setParameter("id", id).setParameter("tenant", tenant).executeUpdate();
        if (n == 0) {
            throw new NotFoundException("Silinmiş kayıt bulunamadı: " + tur.etiket() + " #" + id);
        }
        if (tur == SilinebilirTur.YOKLAMA_OTURUMU) {
            em.createNativeQuery("UPDATE attendance_entry SET silindi_tarihi = NULL, silen = NULL "
                            + "WHERE session_id = :id AND tenant_id = :tenant")
                    .setParameter("id", id).setParameter("tenant", tenant).executeUpdate();
        }
        em.clear(); // birinci seviye onbellekte damgali kopya kalmasin
    }

    // ---------- yardimcilar ----------

    private static void bagliEkle(List<SilmeOnizleme.BagliKayit> liste, String ad, long sayi) {
        if (sayi > 0) {
            liste.add(new SilmeOnizleme.BagliKayit(ad, sayi));
        }
    }

    private static <T> T bul(SilinebilirTur tur, Optional<T> o) {
        return o.orElseThrow(() -> new NotFoundException(tur.etiket() + " bulunamadı"));
    }

    private static void damgala(Optional<? extends SoftDeletable> o, String kim) {
        SoftDeletable e = o.orElseThrow(() -> new NotFoundException("Kayıt bulunamadı"));
        e.sil(kim);
    }

    private static void ayril(Enrollment e) {
        e.setDurum(EnrollmentDurumu.AYRILDI);
        e.setAyrilmaTarihi(LocalDate.now());
    }

    /** timestamptz surucu/Hibernate surumune gore Timestamp, OffsetDateTime veya Instant gelebilir. */
    private static Instant zamanaCevir(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Instant i) {
            return i;
        }
        if (v instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (v instanceof java.time.LocalDateTime l) {
            return l.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalStateException("Beklenmeyen zaman tipi: " + v.getClass());
    }

    private static UUID tenant() {
        UUID t = TenantContext.get();
        if (t == null) {
            throw new NotFoundException("Kurum bağlamı yok");
        }
        return t;
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
