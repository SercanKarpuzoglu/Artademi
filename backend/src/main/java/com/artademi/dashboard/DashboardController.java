package com.artademi.dashboard;

import com.artademi.common.ApiResponse;
import com.artademi.dashboard.dto.DashboardData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Genel Bakis" panel ucu (read-only). Tek uc, her giris yapan IS rolune acik; icerik role gore
 * degisir ve parasal alanlar role gore TIP-duzeyinde filtrelenir (bkz. {@link DashboardService}).
 * Rol kapisi metot duzeyinde DEGIL — uc herkese acik, izolasyon/filtre servistedir; uygun is rolu
 * olmayan 403 alir (super.admin tenant'siz oldugundan {@code /api/**} -> 400).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DashboardData> get() {
        return ApiResponse.ok(service.build());
    }
}
