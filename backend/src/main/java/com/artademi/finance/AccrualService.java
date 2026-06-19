package com.artademi.finance;

import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.dto.AccrualMapper;
import com.artademi.finance.dto.AccrualResponse;
import com.artademi.finance.dto.CreateAccrualRequest;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tahakkuk (accrual) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogrenciId ZORUNLU ve grupId (varsa) ilgili
 * repository'nin {@code findScopedById} metodu ile cozulur; bulunamazsa (baska tenant'a ait VEYA yok)
 * {@link NotFoundException} (-> 404). Boylece baska tenant'in id'siyle tahakkuk olusturulamaz.
 *
 * <p>PARA KURALI: tutar pozitifligi DTO @Positive ile (-> 400) zorlanir.
 *
 * <p>Silme YOK.
 */
@Service
public class AccrualService {

    private final AccrualRepository repository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;

    public AccrualService(AccrualRepository repository, StudentRepository studentRepository,
            GroupRepository groupRepository) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
    }

    /** Yeni tahakkuk olusturur, 201. */
    @Transactional
    public AccrualResponse create(CreateAccrualRequest req) {
        Student ogrenci = resolveStudent(req.ogrenciId());
        Group grup = req.grupId() == null ? null : resolveGroup(req.grupId());

        Accrual saved = repository.save(
                AccrualMapper.toNewEntity(ogrenci, grup, req.donem(), req.tutar(), req.aciklama()));
        return AccrualResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccrualResponse get(Long id) {
        return AccrualResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<AccrualResponse> search(Long ogrenciId, String donem, Long grupId, Pageable pageable) {
        Specification<Accrual> spec = Specification
                .where(AccrualSpecifications.hasOgrenci(ogrenciId))
                .and(AccrualSpecifications.hasDonem(donem))
                .and(AccrualSpecifications.hasGrup(grupId));
        return repository.findAll(spec, pageable)
                .map(AccrualResponse::from);
    }

    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Accrual findOrThrow(Long id) {
        // ONEMLI: findById (PK find) tenant filtresine TABI DEGILDIR; baska tenant'in kaydini sizdirir.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Tahakkuk bulunamadı: " + id));
    }
}
