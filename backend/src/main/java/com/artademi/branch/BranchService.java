package com.artademi.branch;

import com.artademi.branch.dto.BranchMapper;
import com.artademi.branch.dto.BranchResponse;
import com.artademi.branch.dto.CreateBranchRequest;
import com.artademi.branch.dto.UpdateBranchRequest;
import com.artademi.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brans is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik
 * set edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur.
 */
@Service
public class BranchService {

    private final BranchRepository repository;

    public BranchService(BranchRepository repository) {
        this.repository = repository;
    }

    /** Yeni brans olusturur; aktif true ile baslar. */
    @Transactional
    public BranchResponse create(CreateBranchRequest req) {
        Branch saved = repository.save(BranchMapper.toNewEntity(req));
        return BranchResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(Long id) {
        return BranchResponse.from(findOrThrow(id));
    }

    @Transactional
    public BranchResponse update(Long id, UpdateBranchRequest req) {
        Branch branch = findOrThrow(id);
        BranchMapper.applyUpdate(branch, req);
        return BranchResponse.from(branch);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public BranchResponse changeActive(Long id, boolean aktif) {
        Branch branch = findOrThrow(id);
        branch.setAktif(aktif);
        return BranchResponse.from(branch);
    }

    /** Filtreli/sayfali liste; aktif ve q opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<BranchResponse> search(Boolean aktif, String q, Pageable pageable) {
        Specification<Branch> spec = Specification
                .where(BranchSpecifications.hasAktif(aktif))
                .and(BranchSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(BranchResponse::from);
    }

    private Branch findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Branş bulunamadı: " + id));
    }
}
