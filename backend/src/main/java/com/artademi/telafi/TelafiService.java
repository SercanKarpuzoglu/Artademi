package com.artademi.telafi;

import com.artademi.attendance.AttendanceEntry;
import com.artademi.attendance.AttendanceEntryRepository;
import com.artademi.attendance.AttendanceSession;
import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.telafi.dto.TelafiAdayi;
import com.artademi.telafi.dto.TelafiKullanRequest;
import com.artademi.telafi.dto.TelafiResponse;
import com.artademi.telafi.dto.TelafiVerRequest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Telafi ders hakki yonetimi.
 *
 * <p>⚠️ Hak OTOMATIK DOGMAZ; kurum verir ({@link #ver}). Her devamsizliktan otomatik
 * uretilseydi liste kullanilamaz hale gelirdi ve kurumun kendi kurali ezilirdi.
 * {@link #adaylar} yalnizca ONERI listesidir — henuz hak verilmemis devamsizliklari gosterir.
 */
@Service
public class TelafiService {

    /** Aday listesinde ne kadar geriye bakilir. Daha eskisi pratikte telafi edilmiyor. */
    static final int ADAY_GUN = 60;

    private final TelafiHakkiRepository repository;
    private final StudentRepository students;
    private final AttendanceSessionRepository sessions;
    private final AttendanceEntryRepository entries;

    public TelafiService(TelafiHakkiRepository repository, StudentRepository students,
            AttendanceSessionRepository sessions, AttendanceEntryRepository entries) {
        this.repository = repository;
        this.students = students;
        this.sessions = sessions;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public List<TelafiResponse> list(TelafiDurumu durum, Long ogrenciId) {
        LocalDate bugun = LocalDate.now();
        List<TelafiHakki> liste;
        if (ogrenciId != null) {
            liste = repository.ogrenciyeGore(ogrenciId);
        } else if (durum != null) {
            liste = repository.durumaGore(durum);
        } else {
            liste = repository.tumu();
        }
        return liste.stream().map(t -> TelafiResponse.from(t, bugun)).toList();
    }

    /** Panel rozeti: kullanilmayi bekleyen hak sayisi. */
    @Transactional(readOnly = true)
    public long bekleyenSayisi() {
        return repository.countByDurum(TelafiDurumu.BEKLIYOR);
    }

    @Transactional(readOnly = true)
    public TelafiResponse get(Long id) {
        return TelafiResponse.from(bul(id), LocalDate.now());
    }

    /**
     * Telafi hakki tanimlar.
     *
     * <p>Ayni devamsizliktan IKINCI hak verilemez (409) — aksi halde bir devamsizlik iki
     * telafi dersi dogururdu.
     */
    @Transactional
    public TelafiResponse ver(TelafiVerRequest req) {
        Student ogrenci = students.findScopedById(req.ogrenciId())
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + req.ogrenciId()));

        AttendanceSession kaynak = null;
        if (req.kaynakOturumId() != null) {
            kaynak = oturum(req.kaynakOturumId());
            repository.kaynaktanVerilmisMi(ogrenci.getId(), kaynak.getId())
                    .ifPresent(v -> {
                        throw new ConflictException(
                                "Bu devamsızlık için zaten telafi hakkı tanımlanmış.");
                    });
        }

        TelafiHakki hak = TelafiHakki.of(ogrenci, kaynak, LocalDate.now(),
                req.sonKullanmaTarihi(), req.aciklama());
        return TelafiResponse.from(repository.save(hak), LocalDate.now());
    }

    /**
     * Hakki kullanilmis olarak isaretler.
     *
     * <p>Yalnizca BEKLIYOR hak kullanilabilir: kullanilmis hakki tekrar kullanmak ayni
     * telafiyi iki kez saymak, iptal edilmisi kullanmak ise geri alinmis hakki diriltmek olurdu.
     *
     * <p>Suresi dolmus hak da kullanilamaz — sure koymanin anlami budur.
     */
    @Transactional
    public TelafiResponse kullan(Long id, TelafiKullanRequest req) {
        TelafiHakki hak = bul(id);
        LocalDate bugun = LocalDate.now();

        if (hak.getDurum() != TelafiDurumu.BEKLIYOR) {
            throw new ConflictException(hak.getDurum() == TelafiDurumu.KULLANILDI
                    ? "Bu telafi hakkı zaten kullanılmış."
                    : "İptal edilmiş telafi hakkı kullanılamaz.");
        }
        if (hak.suresiDolduMu(bugun)) {
            throw new ConflictException("Bu telafi hakkının süresi dolmuş ("
                    + hak.getSonKullanmaTarihi() + ").");
        }

        AttendanceSession kullanilan = oturum(req.kullanilanOturumId());
        hak.kullan(kullanilan, req.kullanimTarihi() != null ? req.kullanimTarihi() : bugun);
        return TelafiResponse.from(hak, bugun);
    }

    /** Hakki geri alir. Silme YOK: kimin hak kazandigi izi korunur. */
    @Transactional
    public TelafiResponse iptal(Long id) {
        TelafiHakki hak = bul(id);
        if (hak.getDurum() == TelafiDurumu.KULLANILDI) {
            throw new ConflictException("Kullanılmış telafi hakkı iptal edilemez.");
        }
        hak.iptalEt();
        return TelafiResponse.from(hak, LocalDate.now());
    }

    /**
     * Telafi hakki verilebilecek devamsizliklar (son {@value #ADAY_GUN} gun).
     *
     * <p>Bu liste olmadan yonetici "kim gelmemisti" diye yoklama kayitlarini tek tek taramak
     * zorunda kalirdi. ZATEN hak verilmis devamsizliklar listede gorunmez.
     */
    @Transactional(readOnly = true)
    public List<TelafiAdayi> adaylar() {
        LocalDate baslangic = LocalDate.now().minusDays(ADAY_GUN);

        Set<String> hakVerilmis = new HashSet<>();
        repository.hakVerilmisCiftler().forEach(c -> hakVerilmis.add(c[0] + "#" + c[1]));

        return entries.gelmeyenlerAralikta(baslangic, LocalDate.now()).stream()
                .filter(e -> !hakVerilmis.contains(
                        e.getOgrenci().getId() + "#" + e.getSession().getId()))
                .map(e -> new TelafiAdayi(
                        e.getOgrenci().getId(),
                        (e.getOgrenci().getAd() + " " + e.getOgrenci().getSoyad()).trim(),
                        e.getSession().getId(),
                        e.getSession().getTarih(),
                        e.getSession().getGrup() != null ? e.getSession().getGrup().getAd() : null))
                .toList();
    }

    /** ⚠️ findById DEGIL: PK-find tenant filtresine tabi degildir. */
    private TelafiHakki bul(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Telafi hakkı bulunamadı: " + id));
    }

    /** Oturum ayni tenant'a ait mi — capraz-tenant referans korumasi. */
    private AttendanceSession oturum(Long id) {
        return sessions.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Ders bulunamadı: " + id));
    }
}
