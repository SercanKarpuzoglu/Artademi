package com.artademi.student;

import com.artademi.common.exception.NotFoundException;
import com.artademi.student.dto.CreateStudentRequest;
import com.artademi.student.dto.StudentMapper;
import com.artademi.student.dto.StudentResponse;
import com.artademi.student.dto.UpdateStudentRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ogrenci is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik
 * set edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Silme YOK: {@link #changeStatus} ile PASIF'e alinarak veri korunur.
 * Veli zorunlulugu validasyonu DTO uzerindeki {@code @VeliRequired} ile (400) saglanir.
 */
@Service
public class StudentService {

    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    /** Yeni ogrenci olusturur; statu DENEME ile baslar. */
    @Transactional
    public StudentResponse create(CreateStudentRequest req) {
        Student saved = repository.save(StudentMapper.toNewEntity(req));
        return StudentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentResponse.from(findOrThrow(id));
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest req) {
        Student student = findOrThrow(id);
        StudentMapper.applyUpdate(student, req);
        return StudentResponse.from(student);
    }

    /** Manuel statu degisikligi (silme yerine PASIF'e alma dahil). */
    @Transactional
    public StudentResponse changeStatus(Long id, StudentStatus status) {
        Student student = findOrThrow(id);
        student.setStatus(status);
        return StudentResponse.from(student);
    }

    /** Filtreli/sayfali liste; status ve q opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<StudentResponse> search(StudentStatus status, String q, Pageable pageable) {
        Specification<Student> spec = Specification
                .where(StudentSpecifications.hasStatus(status))
                .and(StudentSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(StudentResponse::from);
    }

    /**
     * Kardesler: ayni tenant icinde, kendisi haricinde, ayni anne VEYA baba TC'sine
     * sahip ogrenciler. Bos/null veli TC'leri eslesmemeli.
     */
    @Transactional(readOnly = true)
    public List<StudentResponse> siblings(Long id) {
        Student student = findOrThrow(id);
        String anneTc = blankToNull(student.getAnneTcKimlikNo());
        String babaTc = blankToNull(student.getBabaTcKimlikNo());
        if (anneTc == null && babaTc == null) {
            return List.of();
        }
        return repository.findSiblings(student.getId(), anneTc, babaTc).stream()
                .map(StudentResponse::from)
                .toList();
    }

    private Student findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
