package com.artademi.payout;

import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.finance.PaymentRepository;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.payout.dto.CalculatePayoutRequest;
import com.artademi.payout.dto.PayoutResponse;
import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherHakedis;
import com.artademi.teacher.TeacherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hakedis (payout) is kurallari — Model C. {@code @Transactional} oldugundan cagrildiginda global
 * tenant filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set
 * edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Model C: hakedis tipi GRUBA baglidir. Bir ogretmenin her grubu, GRUBUN {@code hakedisTipi}'ne
 * gore ve ogretmenin O TIPE ait {@link TeacherHakedis} oraniyla hesaplanir. Ayni tipteki gruplar
 * TOPLANIR -> tip basina TEK {@link PayoutResponse}. Boylece cifte sayim imkansizdir.
 * <ul>
 *   <li>SAATLIK: grup oturum sayisi × ogretmen.SAATLIK.saatlikUcret.</li>
 *   <li>OZEL_DERS: grup oturum sayisi × ogretmen.OZEL_DERS.dersBasiUcret.</li>
 *   <li>CIRO_ORANI: gruba bagli tahsilat toplami; netCiro = toplam/(1+kdv/100); × oran/100.</li>
 * </ul>
 * Ogretmende grubun tipine ait oran SATIRI YOKSA o grup ATLANIR (hata degil).
 *
 * <p>Capraz-tenant dogrulamasi (KRITIK): ogretmenId {@code findScopedById} ile cozulur (404). Tum
 * yardimci sorgular JPQL oldugundan tenant filtresine tabidir; baska tenant verisi hesaba KATILMAZ.
 *
 * <p>PARA KURALI (KRITIK): tum parasal/oran matematigi {@link BigDecimal}, scale 2,
 * {@link RoundingMode#HALF_UP}. Asla double/float kullanilmaz.
 *
 * <p>Mukerrer engeli: ayni ogretmen + donem + TIP icin hakediş varsa hesapla -> 409.
 *
 * <p>Silme YOK.
 */
@Service
public class PayoutService {

    /** kdvOrani verilmezse varsayilan KDV yuzdesi (CIRO_ORANI hesabinda). */
    private static final BigDecimal VARSAYILAN_KDV = BigDecimal.valueOf(20);

    private static final BigDecimal YUZ = BigDecimal.valueOf(100);

    private final PayoutRepository repository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final PaymentRepository paymentRepository;

    public PayoutService(PayoutRepository repository, TeacherRepository teacherRepository,
            GroupRepository groupRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            PaymentRepository paymentRepository) {
        this.repository = repository;
        this.teacherRepository = teacherRepository;
        this.groupRepository = groupRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Hesaplar VE kaydeder (her katki saglayan tip icin bir Payout satiri, durum HESAPLANDI), 201.
     * Mukerrer (ogretmen+donem+tip) -> 409. Hicbir grup katki saglamiyorsa -> 400.
     */
    @Transactional
    public List<PayoutResponse> hesapla(CalculatePayoutRequest req) {
        Teacher ogretmen = resolveTeacher(req.ogretmenId());
        String donem = parseDonem(req.donem()).toString();

        List<PayoutHesap> hesaplar = hesapla(ogretmen, donem, req.kdvOrani());
        if (hesaplar.isEmpty()) {
            throw new ValidationException(
                    "Bu öğretmen için bu dönemde hesaplanacak grup/hakediş bulunamadı");
        }

        // Mukerrer kontrolu (tip bazinda) — herhangi biri varsa 409, hicbir satir kaydetme.
        for (PayoutHesap hesap : hesaplar) {
            if (repository.existsByOgretmenAndDonemAndTip(ogretmen.getId(), donem, hesap.hakedisTipi())) {
                throw new ConflictException(
                        "Bu öğretmen için bu dönemde " + hesap.hakedisTipi() + " hakedişi zaten hesaplanmış");
            }
        }

        List<PayoutResponse> sonuc = new ArrayList<>();
        for (PayoutHesap hesap : hesaplar) {
            Payout payout = Payout.create();
            payout.setOgretmen(ogretmen);
            payout.setDonem(hesap.donem());
            payout.setHakedisTipi(hesap.hakedisTipi());
            payout.setHesaplananTutar(hesap.hesaplananTutar());
            payout.setDersSayisi(hesap.dersSayisi());
            payout.setBirimUcret(hesap.birimUcret());
            payout.setToplamTahsilat(hesap.toplamTahsilat());
            payout.setKdvOrani(hesap.kdvOrani());
            payout.setNetCiro(hesap.netCiro());
            payout.setOran(hesap.oran());
            payout.setDurum(PayoutDurumu.HESAPLANDI);
            sonuc.add(PayoutResponse.from(repository.save(payout)));
        }
        return sonuc;
    }

    /** Hesaplar ama KAYDETMEZ (onizleme). Mukerrer kontrolu YOK; satir olusmaz. */
    @Transactional(readOnly = true)
    public List<PayoutResponse> onizle(Long ogretmenId, String donemRaw, BigDecimal kdvOrani) {
        Teacher ogretmen = resolveTeacher(ogretmenId);
        String donem = parseDonem(donemRaw).toString();
        return hesapla(ogretmen, donem, kdvOrani).stream()
                .map(PayoutResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PayoutResponse get(Long id) {
        return PayoutResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<PayoutResponse> search(Long ogretmenId, String donem, PayoutDurumu durum,
            Pageable pageable) {
        Specification<Payout> spec = Specification
                .where(PayoutSpecifications.hasOgretmen(ogretmenId))
                .and(PayoutSpecifications.hasDonem(donem))
                .and(PayoutSpecifications.hasDurum(durum));
        return repository.findAll(spec, pageable)
                .map(PayoutResponse::from);
    }

    /** Hakedisi ODENDI olarak isaretler (odemeTarihi = bugun). */
    @Transactional
    public PayoutResponse ode(Long id) {
        Payout payout = findOrThrow(id);
        payout.setDurum(PayoutDurumu.ODENDI);
        payout.setOdemeTarihi(LocalDate.now());
        return PayoutResponse.from(repository.save(payout));
    }

    // --- Hesaplama cekirdegi (kaydetmeden) — Model C: grup bazinda, tip basina toplanir ---

    /**
     * Ogretmenin gruplarini dolasir; her grubu kendi {@code hakedisTipi}'ne gore hesaplar ve ayni
     * tipteki katkilar TOPLANIR. Sonuc tip basina (≥1 grup katkisi olan tipler) tek {@link PayoutHesap}.
     * Ogretmende grubun tipine ait oran satiri yoksa o grup ATLANIR.
     */
    private List<PayoutHesap> hesapla(Teacher ogretmen, String donem, BigDecimal kdvOraniRaw) {
        YearMonth ym = YearMonth.parse(donem);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        BigDecimal kdvOrani = kdvOraniRaw != null ? kdvOraniRaw : VARSAYILAN_KDV;

        Map<HakedisTipi, TeacherHakedis> oranlar = new EnumMap<>(HakedisTipi.class);
        for (TeacherHakedis h : ogretmen.getHakedisler()) {
            oranlar.put(h.getTip(), h);
        }

        Map<HakedisTipi, TipBirikimi> birikim = new EnumMap<>(HakedisTipi.class);
        for (Group grup : groupRepository.findByOgretmenId(ogretmen.getId())) {
            HakedisTipi tip = grup.getHakedisTipi();
            TeacherHakedis oran = oranlar.get(tip);
            if (oran == null) {
                // Ogretmen bu tipi tanimlamamis -> grup atlanir (hata degil).
                continue;
            }
            TipBirikimi b = birikim.computeIfAbsent(tip, t -> new TipBirikimi());
            switch (tip) {
                case SAATLIK, OZEL_DERS -> {
                    long oturum = attendanceSessionRepository
                            .countByGrupAndTarihAraligi(grup.getId(), from, to);
                    b.dersSayisi += (int) oturum;
                }
                case CIRO_ORANI -> {
                    BigDecimal tahsilat = paymentRepository
                            .sumTutarByGrupAndTarihAraligi(grup.getId(), from, to);
                    b.toplamTahsilat = b.toplamTahsilat.add(tahsilat);
                }
            }
        }

        List<PayoutHesap> sonuc = new ArrayList<>();
        // Sabit, ongorulebilir siralama (enum bildirim sirasi).
        for (HakedisTipi tip : HakedisTipi.values()) {
            TipBirikimi b = birikim.get(tip);
            if (b == null) {
                continue;
            }
            TeacherHakedis oran = oranlar.get(tip);
            sonuc.add(hesaplaTip(ogretmen, donem, tip, oran, b, kdvOrani));
        }
        return sonuc;
    }

    private PayoutHesap hesaplaTip(Teacher ogretmen, String donem, HakedisTipi tip, TeacherHakedis oran,
            TipBirikimi b, BigDecimal kdvOrani) {
        return switch (tip) {
            case SAATLIK -> {
                BigDecimal birimUcret = oran.getSaatlikUcret();
                BigDecimal tutar = birimUcret.multiply(BigDecimal.valueOf(b.dersSayisi))
                        .setScale(2, RoundingMode.HALF_UP);
                yield new PayoutHesap(ogretmen, donem, tip, tutar,
                        b.dersSayisi, birimUcret, null, null, null, null);
            }
            case OZEL_DERS -> {
                BigDecimal birimUcret = oran.getDersBasiUcret();
                BigDecimal tutar = birimUcret.multiply(BigDecimal.valueOf(b.dersSayisi))
                        .setScale(2, RoundingMode.HALF_UP);
                yield new PayoutHesap(ogretmen, donem, tip, tutar,
                        b.dersSayisi, birimUcret, null, null, null, null);
            }
            case CIRO_ORANI -> {
                BigDecimal ciroOrani = oran.getCiroOrani();
                BigDecimal toplamTahsilat = b.toplamTahsilat.setScale(2, RoundingMode.HALF_UP);
                // netCiro = toplamTahsilat / (1 + kdvOrani/100)
                BigDecimal factor = BigDecimal.ONE.add(kdvOrani.divide(YUZ));
                BigDecimal netCiro = toplamTahsilat.divide(factor, 2, RoundingMode.HALF_UP);
                // hesaplananTutar = netCiro × oran / 100
                BigDecimal tutar = netCiro.multiply(ciroOrani).divide(YUZ)
                        .setScale(2, RoundingMode.HALF_UP);
                yield new PayoutHesap(ogretmen, donem, tip, tutar,
                        null, null, toplamTahsilat, kdvOrani.setScale(2, RoundingMode.HALF_UP),
                        netCiro, ciroOrani.setScale(2, RoundingMode.HALF_UP));
            }
        };
    }

    private Teacher resolveTeacher(Long ogretmenId) {
        return teacherRepository.findScopedById(ogretmenId)
                .orElseThrow(() -> new NotFoundException("Öğretmen bulunamadı: " + ogretmenId));
    }

    private YearMonth parseDonem(String donem) {
        try {
            return YearMonth.parse(donem);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("Geçersiz dönem formatı (YYYY-MM)");
        }
    }

    private Payout findOrThrow(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Hakediş bulunamadı: " + id));
    }

    /** Tip bazinda gecici birikim (SAATLIK/OZEL_DERS -> dersSayisi; CIRO_ORANI -> toplamTahsilat). */
    private static final class TipBirikimi {
        private int dersSayisi;
        private BigDecimal toplamTahsilat = BigDecimal.ZERO;
    }
}
