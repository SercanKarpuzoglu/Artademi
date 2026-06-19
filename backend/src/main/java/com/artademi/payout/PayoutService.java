package com.artademi.payout;

import com.artademi.attendance.AttendanceSessionRepository;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.finance.PaymentRepository;
import com.artademi.payout.dto.CalculatePayoutRequest;
import com.artademi.payout.dto.PayoutResponse;
import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hakedis (payout) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogretmenId ZORUNLU; {@code findScopedById}
 * ile cozulur, bulunamazsa -> 404. Tum yardimci sorgular (oturum sayisi, tahsilat toplami) JPQL
 * oldugundan tenant filtresine tabidir; baska tenant'in verisi hesaba KATILMAZ.
 *
 * <p>PARA KURALI (KRITIK): tum parasal/oran matematigi {@link BigDecimal}, scale 2,
 * {@link RoundingMode#HALF_UP}. Asla double/float kullanilmaz.
 *
 * <p>Hesaplama, ogretmenin {@code hakedisTipi}'ne gore yapilir (hesaplama aninda KOPYALANIR):
 * <ul>
 *   <li>SAATLIK: donemdeki oturum sayisi × saatlikUcret.</li>
 *   <li>CIRO_ORANI: donemdeki tahsilat toplami (KDV haric) × ciroOrani%.</li>
 * </ul>
 *
 * <p>Mukerrer engeli: ayni ogretmen + donem icin hakediş varsa hesapla -> 409.
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
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final PaymentRepository paymentRepository;

    public PayoutService(PayoutRepository repository, TeacherRepository teacherRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            PaymentRepository paymentRepository) {
        this.repository = repository;
        this.teacherRepository = teacherRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Hesaplar VE kaydeder (durum HESAPLANDI), 201. Mukerrer (ogretmen+donem) -> 409. */
    @Transactional
    public PayoutResponse hesapla(CalculatePayoutRequest req) {
        Teacher ogretmen = resolveTeacher(req.ogretmenId());
        String donem = parseDonem(req.donem()).toString();

        if (repository.existsByOgretmenAndDonem(ogretmen.getId(), donem)) {
            throw new ConflictException("Bu öğretmen için bu dönemde hakediş zaten hesaplanmış");
        }

        PayoutHesap hesap = hesapla(ogretmen, donem, req.kdvOrani());

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

        return PayoutResponse.from(repository.save(payout));
    }

    /** Hesaplar ama KAYDETMEZ (onizleme). Mukerrer kontrolu YOK; satir olusmaz. */
    @Transactional(readOnly = true)
    public PayoutResponse onizle(Long ogretmenId, String donemRaw, BigDecimal kdvOrani) {
        Teacher ogretmen = resolveTeacher(ogretmenId);
        String donem = parseDonem(donemRaw).toString();
        return PayoutResponse.from(hesapla(ogretmen, donem, kdvOrani));
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

    // --- Hesaplama cekirdegi (kaydetmeden) ---

    private PayoutHesap hesapla(Teacher ogretmen, String donem, BigDecimal kdvOraniRaw) {
        YearMonth ym = YearMonth.parse(donem);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        HakedisTipi tip = ogretmen.getHakedisTipi();

        if (tip == HakedisTipi.SAATLIK) {
            long oturum = attendanceSessionRepository
                    .countByOgretmenAndTarihAraligi(ogretmen.getId(), from, to);
            int dersSayisi = (int) oturum;
            BigDecimal birimUcret = ogretmen.getSaatlikUcret();
            BigDecimal hesaplananTutar = birimUcret
                    .multiply(BigDecimal.valueOf(dersSayisi))
                    .setScale(2, RoundingMode.HALF_UP);
            return new PayoutHesap(ogretmen, donem, tip, hesaplananTutar,
                    dersSayisi, birimUcret, null, null, null, null);
        }

        // CIRO_ORANI
        BigDecimal kdvOrani = kdvOraniRaw != null ? kdvOraniRaw : VARSAYILAN_KDV;
        BigDecimal oran = ogretmen.getCiroOrani();
        BigDecimal toplamTahsilat = paymentRepository
                .sumTutarByOgretmenAndTarihAraligi(ogretmen.getId(), from, to)
                .setScale(2, RoundingMode.HALF_UP);
        // netCiro = toplamTahsilat / (1 + kdvOrani/100)
        BigDecimal factor = BigDecimal.ONE.add(kdvOrani.divide(YUZ));
        BigDecimal netCiro = toplamTahsilat.divide(factor, 2, RoundingMode.HALF_UP);
        // hesaplananTutar = netCiro × oran / 100
        BigDecimal hesaplananTutar = netCiro.multiply(oran).divide(YUZ)
                .setScale(2, RoundingMode.HALF_UP);
        return new PayoutHesap(ogretmen, donem, tip, hesaplananTutar,
                null, null, toplamTahsilat, kdvOrani.setScale(2, RoundingMode.HALF_UP),
                netCiro, oran.setScale(2, RoundingMode.HALF_UP));
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
}
