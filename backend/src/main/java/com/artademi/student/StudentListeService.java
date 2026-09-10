package com.artademi.student;

import com.artademi.attendance.AttendanceEntryRepository;
import com.artademi.attendance.YoklamaDurumu;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.finance.AccrualRepository;
import com.artademi.finance.PaymentRepository;
import com.artademi.student.dto.StudentListeSatiri;
import com.artademi.student.dto.StudentListeSatiri.GrupRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ogrenci listesi zenginlestirme (Dalga B). Sayfa once {@link StudentRepository} ile alinir, sonra o
 * sayfadaki id'ler icin UC toplu sorgu calisir: aktif kayitlar (grup adlari), tahakkuk/odeme
 * toplamlari (bakiye), son 90 gunun yoklamalari (devamsizlik serisi). Sayfa basina sabit sorgu
 * sayisi; ogrenci basina sorgu YOK.
 *
 * <p>Tum sorgular JPQL -> global tenant filtresi otomatik. {@code @Transactional} zorunlu (filtre
 * aktif oturumda calisir; lazy grup erisimi de burada).
 *
 * <p>PARA: bakiye yalnizca {@code paraGorebilir} ise hesaplanir ve doner; aksi halde null (on buroya
 * parasal veri HIC gonderilmez, gizlenmez).
 */
@Service
public class StudentListeService {

    /** Devamsizlik serisi icin bakilan pencere (gun). */
    static final int SERI_PENCERESI_GUN = 90;

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AccrualRepository accrualRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceEntryRepository attendanceEntryRepository;

    public StudentListeService(StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            AccrualRepository accrualRepository,
            PaymentRepository paymentRepository,
            AttendanceEntryRepository attendanceEntryRepository) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.accrualRepository = accrualRepository;
        this.paymentRepository = paymentRepository;
        this.attendanceEntryRepository = attendanceEntryRepository;
    }

    @Transactional(readOnly = true)
    public Page<StudentListeSatiri> liste(StudentStatus status, String q, Pageable pageable,
            boolean paraGorebilir) {
        Specification<Student> spec = Specification
                .where(StudentSpecifications.hasStatus(status))
                .and(StudentSpecifications.matchesText(q));
        Page<Student> sayfa = studentRepository.findAll(spec, pageable);
        List<Long> ids = sayfa.getContent().stream().map(Student::getId).toList();
        if (ids.isEmpty()) {
            return sayfa.map(s -> satir(s, List.of(), null, null));
        }

        Map<Long, List<GrupRef>> gruplar = new HashMap<>();
        for (Enrollment e : enrollmentRepository.findAktifByOgrenciIds(ids)) {
            gruplar.computeIfAbsent(e.getOgrenci().getId(), k -> new ArrayList<>())
                    .add(new GrupRef(e.getGrup().getId(), e.getGrup().getAd()));
        }

        Map<Long, BigDecimal> bakiyeler = paraGorebilir ? bakiyeler(ids) : Map.of();
        Map<Long, Integer> seriler = devamsizlikSerileri(ids);

        return sayfa.map(s -> satir(s,
                gruplar.getOrDefault(s.getId(), List.of()),
                paraGorebilir ? bakiyeler.getOrDefault(s.getId(), BigDecimal.ZERO.setScale(2)) : null,
                seriler.get(s.getId())));
    }

    private Map<Long, BigDecimal> bakiyeler(List<Long> ids) {
        Map<Long, BigDecimal> sonuc = new HashMap<>();
        for (Object[] r : accrualRepository.sumTutarGroupByOgrenciIn(ids)) {
            sonuc.merge((Long) r[0], (BigDecimal) r[1], BigDecimal::add);
        }
        for (Object[] r : paymentRepository.sumTutarGroupByOgrenciIn(ids)) {
            sonuc.merge((Long) r[0], ((BigDecimal) r[1]).negate(), BigDecimal::add);
        }
        sonuc.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        return sonuc;
    }

    /**
     * Her ogrenci icin en yeni yoklamadan geriye ardisik GELMEDI sayisi (negatif). Satirlar tarih
     * DESC geldiginden ilk kirilmada durulur; GELDI veya IZINLI seriyi bitirir.
     */
    private Map<Long, Integer> devamsizlikSerileri(List<Long> ids) {
        LocalDate baslangic = LocalDate.now().minusDays(SERI_PENCERESI_GUN);
        Map<Long, List<YoklamaDurumu>> durumlar = new LinkedHashMap<>();
        for (Object[] r : attendanceEntryRepository.sonDurumlar(ids, baslangic)) {
            durumlar.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((YoklamaDurumu) r[1]);
        }
        Map<Long, Integer> seriler = new HashMap<>();
        durumlar.forEach((ogrenciId, liste) -> {
            int seri = 0;
            for (YoklamaDurumu d : liste) {
                if (d != YoklamaDurumu.GELMEDI) {
                    break;
                }
                seri++;
            }
            seriler.put(ogrenciId, -seri);
        });
        return seriler;
    }

    private static StudentListeSatiri satir(Student s, List<GrupRef> gruplar, BigDecimal bakiye,
            Integer seri) {
        return new StudentListeSatiri(s.getId(), s.getAd(), s.getSoyad(), s.getTcKimlikNo(),
                s.getStatus(), s.isKaraListe(), s.getKaraListeAciklama(), gruplar, bakiye, seri);
    }
}
