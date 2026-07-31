package com.artademi.platform;

import com.artademi.common.ApiResponse;
import com.artademi.platform.dto.PlatformDashboardResponse;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform genel bakis ucu — {@code GET /api/platform/dashboard} (SADECE SUPER_ADMIN).
 *
 * <p>{@code /api/platform/**} tenant-muafiyeti zaten TenantWebConfig'te tanimli: super.admin'in
 * tenant_id'si yoktur.
 */
@RestController
@RequestMapping("/api/platform/dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformDashboardController {

    private final PlatformDashboardService service;

    public PlatformDashboardController(PlatformDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PlatformDashboardResponse> dashboard() {
        return ApiResponse.ok(service.build(LocalDate.now()));
    }
}
