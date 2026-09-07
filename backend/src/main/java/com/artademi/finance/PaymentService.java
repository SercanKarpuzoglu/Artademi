package com.artademi.finance;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.finance.dto.CreatePaymentRequest;
import com.artademi.finance.dto.PaymentMapper;
import com.artademi.finance.dto.PaymentResponse;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tahsilat (payment) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogrenciId ZORUNLU; accrualId/grupId (varsa)
 * ilgili repository'nin {@code findScopedById} metodu ile cozulur; bulunamazsa -> 404.
 *
 * <p>Is kurali: accrual verilirse tahakkugun ogrencisi payment ogrencisi ile AYNI olmalidir; degilse
 * {@link ValidationException} (-> 400).
 *
 * <p>{@code odemeTarihi} verilmezse bugun (LocalDate.now()) kullanilir. tutar pozitifligi DTO @Positive
 * ile (-> 400).
 *
 * <p>Silme YOK.
 */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final AccrualRepository accrualRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final com.artademi.kasa.KasaRepository kasaRepository;

    public PaymentService(PaymentRepository repository, AccrualRepository accrualRepository,
            StudentRepository studentRepository, GroupRepository groupRepository,
            com.artademi.kasa.KasaRepository kasaRepository) {
        this.repository = repository;
        this.accrualRepository = accrualRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.kasaRepository = kasaRepository;
    }

    /** Yeni tahsilat olusturur, 201. */
    @Transactional
    public PaymentResponse create(CreatePaymentRequest req) {
        Student ogrenci = resolveStudent(req.ogrenciId());
        Group grup = req.grupId() == null ? null : resolveGroup(req.grupId());

        Accrual accrual = null;
        if (req.accrualId() != null) {
            accrual = accrualRepository.findScopedById(req.accrualId())
                    .orElseThrow(() -> new NotFoundException("Tahakkuk bulunamadı: " + req.accrualId()));
            // Tahakkuk farkli ogrenciye aitse tahsilat ona baglanamaz -> 400.
            if (!accrual.getOgrenci().getId().equals(ogrenci.getId())) {
                throw new ValidationException("Tahakkuk bu öğrenciye ait değil");
            }
        }

        LocalDate odemeTarihi = req.odemeTarihi() != null ? req.odemeTarihi() : LocalDate.now();
        Payment yeni = PaymentMapper.toNewEntity(
                ogrenci, accrual, grup, req.tutar(), odemeTarihi, req.odemeYontemi(), req.aciklama());
        yeni.setKasa(resolveKasa(req.kasaId()));
        Payment saved = repository.save(yeni);
        return PaymentResponse.from(saved);
    }

    /**
     * Kasa secildiyse AYNI tenant'a ait oldugunu dogrular.
     *
     * <p>⚠️ findScopedById: FK tek basina yabanci kasa referansini engellemez; istemci baska
     * kurumun kasa id'sini gondererek capraz-tenant bag kuramamalidir.
     */
    private com.artademi.kasa.Kasa resolveKasa(Long kasaId) {
        if (kasaId == null) {
            return null;
        }
        return kasaRepository.findScopedById(kasaId)
                .orElseThrow(() -> new NotFoundException("Kasa bulunamadı: " + kasaId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(Long ogrenciId, LocalDate from, LocalDate to,
            OdemeYontemi yontem, Pageable pageable) {
        Specification<Payment> spec = Specification
                .where(PaymentSpecifications.hasOgrenci(ogrenciId))
                .and(PaymentSpecifications.tarihGte(from))
                .and(PaymentSpecifications.tarihLte(to))
                .and(PaymentSpecifications.hasYontem(yontem));
        return repository.findAll(spec, pageable)
                .map(PaymentResponse::from);
    }

    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Payment findOrThrow(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Tahsilat bulunamadı: " + id));
    }
}
