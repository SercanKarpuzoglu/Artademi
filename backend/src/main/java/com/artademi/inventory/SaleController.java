package com.artademi.inventory;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.inventory.dto.CreateSaleRequest;
import com.artademi.inventory.dto.SaleResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Satis ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki (PARA HASSAS): tum uclar (yazma VE okuma) yalnizca ADMIN / FRONTDESK_ACCOUNTING.
 * FRONTDESK ve TEACHER para gormez (403).
 *
 * <p>DELETE/PUT YOK: satis degismez/silinmez.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    /** Yeni satis olustur (stok dusulur), 201. */
    @PostMapping("/api/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SaleResponse> create(@Valid @RequestBody CreateSaleRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek satis (yoksa 404). */
    @GetMapping("/api/sales/{id}")
    public ApiResponse<SaleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Filtreli/sayfali liste: ?urunId=&ogrenciId=&from=&to=&page=0&size=20 (satis tarihine gore azalan). */
    @GetMapping("/api/sales")
    public ApiResponse<List<SaleResponse>> list(
            @RequestParam(required = false) Long urunId,
            @RequestParam(required = false) Long ogrenciId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("satisTarihi").descending());
        Page<SaleResponse> result = service.search(urunId, ogrenciId, from, to, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
