package com.artademi.finance;

import com.artademi.common.ApiResponse;
import com.artademi.finance.dto.BalanceResponse;
import com.artademi.finance.dto.FinanceSummaryResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ogrenci finans/bakiye ucu (finance modulunde; StudentController'a DOKUNULMAZ). Ince controller.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; ogrenci serviste {@code findScopedById} ile tenant-guvenli
 * cozulur (baska tenant / yok -> 404).
 *
 * <p>Yetki (PARA HASSAS): yalnizca ADMIN / FRONTDESK_ACCOUNTING. FRONTDESK ve TEACHER para gormez (403).
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class StudentFinanceController {

    private final StudentFinanceService service;

    public StudentFinanceController(StudentFinanceService service) {
        this.service = service;
    }

    /** Ogrenci bakiyesi: {ogrenciId, toplamTahakkuk, toplamOdeme, bakiye} (BigDecimal scale 2). */
    @GetMapping("/api/students/{studentId}/balance")
    public ApiResponse<BalanceResponse> balance(@PathVariable Long studentId) {
        return ApiResponse.ok(service.balance(studentId));
    }

    /** Ogrenci finans ozeti: {ogrenciId, tahakkuklar[], odemeler[], bakiye}. */
    @GetMapping("/api/students/{studentId}/finance")
    public ApiResponse<FinanceSummaryResponse> finance(@PathVariable Long studentId) {
        return ApiResponse.ok(service.summary(studentId));
    }
}
