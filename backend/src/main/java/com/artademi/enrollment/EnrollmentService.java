package com.artademi.enrollment;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.dto.CreateEnrollmentRequest;
import com.artademi.enrollment.dto.EnrollmentMapper;
import com.artademi.enrollment.dto.EnrollmentResponse;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.student.StudentStatus;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kayit (enrollment) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogrenciId ve grupId ilgili repository'nin
 * {@code findScopedById} metodu ile cozulur; bulunamazsa (baska tenant'a ait VEYA yok)
 * {@link NotFoundException} (-> 404). Boylece baska tenant'in ogrenci/grup id'siyle kayit
 * olusturulamaz (sizinti yok).
 *
 * <p>Is kurallari (create):
 * <ol>
 *   <li>Ogrenci statusu AKTIF veya DENEME degilse (PASIF/DONDURULMUS) -> 400 ValidationException.</li>
 *   <li>Ayni ogrenci+grup icin AKTIF kayit varsa -> 409 ConflictException (AYRILDI varsa yeni acilir;
 *       DB tarafinda partial unique index ile de zorlanir).</li>
 * </ol>
 *
 * <p>Silme YOK: ayrilma {@link #leave} ile durum=AYRILDI + ayrilmaTarihi=bugun olarak yapilir.
 */
@Service
public class EnrollmentService {

    /** Gruba yazilmaya uygun ogrenci statuleri (PASIF/DONDURULMUS uygun degil). */
    private static final Set<StudentStatus> YAZILABILIR_STATULER =
            Set.of(StudentStatus.AKTIF, StudentStatus.DENEME);

    private final EnrollmentRepository repository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;

    public EnrollmentService(EnrollmentRepository repository, StudentRepository studentRepository,
            GroupRepository groupRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
    }

    /** Yeni kayit olusturur; durum AKTIF ile baslar. */
    @Transactional
    public EnrollmentResponse create(CreateEnrollmentRequest req) {
        Student ogrenci = resolveStudent(req.ogrenciId());
        Group grup = resolveGroup(req.grupId());

        if (!YAZILABILIR_STATULER.contains(ogrenci.getStatus())) {
            throw new ValidationException("Bu statüdeki öğrenci gruba yazılamaz");
        }
        if (repository.existsAktifByOgrenciAndGrup(ogrenci.getId(), grup.getId())) {
            throw new ConflictException("Öğrenci bu gruba zaten aktif olarak kayıtlı");
        }

        Enrollment saved = repository.save(
                EnrollmentMapper.toNewEntity(ogrenci, grup, req.kayitTarihi()));
        return EnrollmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(Long id) {
        return EnrollmentResponse.from(findOrThrow(id));
    }

    /** Ayrilma: kayit silinmez; durum AYRILDI, ayrilmaTarihi bugun. */
    @Transactional
    public EnrollmentResponse leave(Long id) {
        Enrollment e = findOrThrow(id);
        e.setDurum(EnrollmentDurumu.AYRILDI);
        e.setAyrilmaTarihi(LocalDate.now());
        return EnrollmentResponse.from(e);
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> search(Long ogrenciId, Long grupId, EnrollmentDurumu durum,
            Pageable pageable) {
        Specification<Enrollment> spec = Specification
                .where(EnrollmentSpecifications.hasOgrenci(ogrenciId))
                .and(EnrollmentSpecifications.hasGrup(grupId))
                .and(EnrollmentSpecifications.hasDurum(durum));
        return repository.findAll(spec, pageable)
                .map(EnrollmentResponse::from);
    }

    /**
     * ogrenciId'yi tenant-guvenli ({@code findScopedById}) cozer. Bulunamazsa (baska tenant'a ait
     * veya yok) -> 404, sizinti yok.
     */
    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Enrollment findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Kayıt bulunamadı: " + id));
    }
}
