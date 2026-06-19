package com.artademi.inventory;

import com.artademi.common.exception.NotFoundException;
import com.artademi.inventory.dto.CreateProductRequest;
import com.artademi.inventory.dto.ProductMapper;
import com.artademi.inventory.dto.ProductResponse;
import com.artademi.inventory.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Urun is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant filtresi aktif
 * oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir (bkz.
 * TenantAware) — burada ELLE yonetilmez.
 *
 * <p>PK-find KURALI: id ile erisim ASLA findById ile yapilmaz (Hibernate filtresine tabi degil,
 * sizinti olur); her zaman {@code findScopedById} ile -> baska tenant'in kaydi 404.
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur. Stok mutlak atama ile
 * ({@link #updateStock}) yonetilir; PUT stoga/aktiflige DOKUNMAZ.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Yeni urun olusturur; aktif true ile baslar. */
    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        Product saved = repository.save(ProductMapper.toNewEntity(req));
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    /** Urun guncelle (stok ve aktiflik degismez). */
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest req) {
        Product product = findOrThrow(id);
        ProductMapper.applyUpdate(product, req);
        return ProductResponse.from(product);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public ProductResponse changeActive(Long id, boolean aktif) {
        Product product = findOrThrow(id);
        product.setAktif(aktif);
        return ProductResponse.from(product);
    }

    /** Stogun MUTLAK ATANMASI (artirma/azaltma degil; verilen deger yeni stok olur). */
    @Transactional
    public ProductResponse updateStock(Long id, int stokAdedi) {
        Product product = findOrThrow(id);
        product.setStokAdedi(stokAdedi);
        return ProductResponse.from(product);
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<ProductResponse> search(Boolean aktif, String q, Pageable pageable) {
        Specification<Product> spec = Specification
                .where(ProductSpecifications.hasAktif(aktif))
                .and(ProductSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(ProductResponse::from);
    }

    private Product findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Ürün bulunamadı: " + id));
    }
}
