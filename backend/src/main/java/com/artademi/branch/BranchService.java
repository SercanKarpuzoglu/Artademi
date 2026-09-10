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
import com.artademi.donem.Donem;
import com.artademi.donem.DonemRepository;

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

    private final DonemRepository donemRepository;

    public BranchService(BranchRepository repository, DonemRepository donemRepository) {
        this.repository = repository;
        this.donemRepository = donemRepository;
    }

    /** Donem opsiyonel; verildiyse tenant-guvenli cozulur (yoksa 404). */
    private Donem resolveDonem(Long donemId) {
        if (donemId == null) {
            return null;
        }
        return donemRepository.findScopedById(donemId)
                .orElseThrow(() -> new NotFoundException("Dönem bulunamadı: " + donemId));
    }

    /** Yeni brans olusturur; aktif true ile baslar. */
    @Transactional
    public BranchResponse create(CreateBranchRequest req) {
        Branch saved = repository.save(BranchMapper.toNewEntity(req, resolveDonem(req.donemId())));
        return BranchResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(Long id) {
        return BranchResponse.from(findOrThrow(id));
    }

    @Transactional
    public BranchResponse update(Long id, UpdateBranchRequest req) {
        Branch branch = findOrThrow(id);
        BranchMapper.applyUpdate(branch, req, resolveDonem(req.donemId()));
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
