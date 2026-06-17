package com.artademi.teacher;

import com.artademi.branch.Branch;
import com.artademi.branch.BranchRepository;
import com.artademi.common.exception.NotFoundException;
import com.artademi.teacher.dto.CreateTeacherRequest;
import com.artademi.teacher.dto.TeacherMapper;
import com.artademi.teacher.dto.TeacherResponse;
import com.artademi.teacher.dto.UpdateTeacherRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ogretmen is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik
 * set edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant brans dogrulamasi (KRITIK): create/update'te gelen her bransId
 * {@link BranchRepository#findScopedById} ile cozulur; bulunamazsa (baska tenant'a ait VEYA
 * yok) {@link NotFoundException} (-> 404). Boylece baska tenant'in brans id'siyle ogretmen
 * olusturulamaz/guncellenemez (sizinti yok).
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur.
 */
@Service
public class TeacherService {

    private final TeacherRepository repository;
    private final BranchRepository branchRepository;

    public TeacherService(TeacherRepository repository, BranchRepository branchRepository) {
        this.repository = repository;
        this.branchRepository = branchRepository;
    }

    /** Yeni ogretmen olusturur; aktif true ile baslar. */
    @Transactional
    public TeacherResponse create(CreateTeacherRequest req) {
        List<Branch> branches = resolveBranches(req.bransIds());
        Teacher saved = repository.save(TeacherMapper.toNewEntity(req, branches));
        return TeacherResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TeacherResponse get(Long id) {
        return TeacherResponse.from(findOrThrow(id));
    }

    @Transactional
    public TeacherResponse update(Long id, UpdateTeacherRequest req) {
        Teacher teacher = findOrThrow(id);
        List<Branch> branches = resolveBranches(req.bransIds());
        TeacherMapper.applyUpdate(teacher, req, branches);
        return TeacherResponse.from(teacher);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public TeacherResponse changeActive(Long id, boolean aktif) {
        Teacher teacher = findOrThrow(id);
        teacher.setAktif(aktif);
        return TeacherResponse.from(teacher);
    }

    /** Filtreli/sayfali liste; aktif, q ve bransId opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<TeacherResponse> search(Boolean aktif, String q, Long bransId, Pageable pageable) {
        Specification<Teacher> spec = Specification
                .where(TeacherSpecifications.hasAktif(aktif))
                .and(TeacherSpecifications.matchesText(q))
                .and(TeacherSpecifications.hasBrans(bransId));
        return repository.findAll(spec, pageable)
                .map(TeacherResponse::from);
    }

    /**
     * Verilen brans id'lerini tenant-guvenli ({@code findScopedById}) cozer. Yinelenenler
     * elenir. Herhangi biri bulunamazsa (baska tenant'a ait veya yok) -> 404, sizinti yok.
     */
    private List<Branch> resolveBranches(List<Long> bransIds) {
        if (bransIds == null || bransIds.isEmpty()) {
            return List.of();
        }
        List<Branch> branches = new ArrayList<>();
        for (Long bransId : new LinkedHashSet<>(bransIds)) {
            if (bransId == null) {
                continue;
            }
            Branch branch = branchRepository.findScopedById(bransId)
                    .orElseThrow(() -> new NotFoundException("Branş bulunamadı: " + bransId));
            branches.add(branch);
        }
        return branches;
    }

    private Teacher findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Öğretmen bulunamadı: " + id));
    }
}
