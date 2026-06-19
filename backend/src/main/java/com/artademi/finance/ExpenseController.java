package com.artademi.finance;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.finance.dto.CreateExpenseRequest;
import com.artademi.finance.dto.ExpenseResponse;
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
 * Gider (expense) ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki (PARA HASSAS): tum uclar (yazma VE okuma) yalnizca ADMIN / FRONTDESK_ACCOUNTING.
 * FRONTDESK ve TEACHER para gormez (403).
 *
 * <p>DELETE YOK.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    /** Yeni gider olustur, 201. */
    @PostMapping("/api/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek gider (yoksa 404). */
    @GetMapping("/api/expenses/{id}")
    public ApiResponse<ExpenseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Filtreli/sayfali liste: ?from=&to=&kategori=&page=0&size=20 (hepsi opsiyonel). */
    @GetMapping("/api/expenses")
    public ApiResponse<List<ExpenseResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String kategori,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("giderTarihi").descending());
        Page<ExpenseResponse> result = service.search(from, to, kategori, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
