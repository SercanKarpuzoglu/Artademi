package com.artademi.sube;

import com.artademi.common.exception.NotFoundException;
import com.artademi.sube.dto.CreateSubeRequest;
import com.artademi.sube.dto.SubeMapper;
import com.artademi.sube.dto.SubeResponse;
import com.artademi.sube.dto.UpdateSubeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sube is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant filtresi
 * aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir.
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur — pasif sube, ona
 * bagli salon ve gruplarin gecmisini bozmadan listelerden duser.
 */
@Service
public class SubeService {

    private final SubeRepository repository;

    public SubeService(SubeRepository repository) {
        this.repository = repository;
    }

    /** Yeni sube olusturur; aktif true ile baslar. */
    @Transactional
    public SubeResponse create(CreateSubeRequest req) {
        return SubeResponse.from(repository.save(SubeMapper.toNewEntity(req)));
    }

    @Transactional(readOnly = true)
    public SubeResponse get(Long id) {
        return SubeResponse.from(findOrThrow(id));
    }

    @Transactional
    public SubeResponse update(Long id, UpdateSubeRequest req) {
        Sube sube = findOrThrow(id);
        SubeMapper.applyUpdate(sube, req);
        return SubeResponse.from(sube);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public SubeResponse changeActive(Long id, boolean aktif) {
        Sube sube = findOrThrow(id);
        sube.setAktif(aktif);
        return SubeResponse.from(sube);
    }

    /** Filtreli/sayfali liste; aktif ve q opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<SubeResponse> search(Boolean aktif, String q, Pageable pageable) {
        Specification<Sube> spec = Specification
                .where(SubeSpecifications.hasAktif(aktif))
                .and(SubeSpecifications.matchesText(q));
        return repository.findAll(spec, pageable).map(SubeResponse::from);
    }

    private Sube findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Şube bulunamadı: " + id));
    }
}
