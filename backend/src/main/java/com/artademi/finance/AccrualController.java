package com.artademi.finance;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.finance.dto.AccrualResponse;
import com.artademi.finance.dto.CreateAccrualRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * Tahakkuk (accrual) ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
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
public class AccrualController {

    private final AccrualService service;

    public AccrualController(AccrualService service) {
        this.service = service;
    }

    /** Yeni tahakkuk olustur, 201. */
    @PostMapping("/api/accruals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccrualResponse> create(@Valid @RequestBody CreateAccrualRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek tahakkuk (yoksa 404). */
    @GetMapping("/api/accruals/{id}")
    public ApiResponse<AccrualResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Filtreli/sayfali liste: ?ogrenciId=&donem=&grupId=&page=0&size=20 (hepsi opsiyonel). */
    @GetMapping("/api/accruals")
    public ApiResponse<List<AccrualResponse>> list(
            @RequestParam(required = false) Long ogrenciId,
            @RequestParam(required = false) String donem,
            @RequestParam(required = false) Long grupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<AccrualResponse> result = service.search(ogrenciId, donem, grupId, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
