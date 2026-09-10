package com.artademi.finance;

import com.artademi.common.ApiResponse;
import com.artademi.finance.dto.GelirOzetiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gelirler ozeti ucu. Ince controller. Tenant ASLA parametre olarak ALINMAZ (JWT'den).
 *
 * <p>Yetki: parasal veri -> ADMIN + FRONTDESK_ACCOUNTING; FRONTDESK/TEACHER 403.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class GelirOzetiController {

    private final GelirOzetiService service;

    public GelirOzetiController(GelirOzetiService service) {
        this.service = service;
    }

    /** ?from=YYYY-MM-DD&to=YYYY-MM-DD (ikisi de zorunlu). */
    @GetMapping("/api/finance/gelir-ozeti")
    public ApiResponse<GelirOzetiResponse> ozet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.ozet(from, to));
    }
}
