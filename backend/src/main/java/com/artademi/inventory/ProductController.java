package com.artademi.inventory;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.inventory.dto.CreateProductRequest;
import com.artademi.inventory.dto.ProductResponse;
import com.artademi.inventory.dto.UpdateActiveRequest;
import com.artademi.inventory.dto.UpdateProductRequest;
import com.artademi.inventory.dto.UpdateStockRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Urun tanim ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki METOT duzeyinde ayrilir (yazma ve okuma FARKLI roller): yazma (POST/PUT/PATCH) yalnizca
 * ADMIN; okuma (GET) ADMIN/FRONTDESK_ACCOUNTING. FRONTDESK ve TEACHER hicbirine erisemez (403).
 *
 * <p>DELETE YOK: silme yerine PATCH /{id}/active ile pasiflestirilir; stok PATCH /{id}/stok ile
 * mutlak atanir (veri korunur).
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Yeni urun olustur (aktif true), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek urun (yoksa 404). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Urun guncelle (stok ve aktiflik degismez). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Aktiflik degisikligi (pasiflestirme dahil). */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> changeActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return ApiResponse.ok(service.changeActive(id, request.aktif()));
    }

    /** Stok mutlak atama. */
    @PatchMapping("/{id}/stok")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProductResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request) {
        return ApiResponse.ok(service.updateStock(id, request.stokAdedi()));
    }

    /** Filtreli/sayfali liste: ?aktif=&q=&page=0&size=20 (q = ad icinde ara; ad'a gore artan). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
    public ApiResponse<List<ProductResponse>> list(
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("ad").ascending());
        Page<ProductResponse> result = service.search(aktif, q, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
