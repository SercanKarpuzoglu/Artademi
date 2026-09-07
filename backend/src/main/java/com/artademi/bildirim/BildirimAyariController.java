package com.artademi.bildirim;

import com.artademi.bildirim.dto.BildirimAyariRequest;
import com.artademi.bildirim.dto.BildirimAyariResponse;
import com.artademi.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurumun otomatik bildirim tercihleri.
 *
 * <p>Yalnizca ADMIN: bu ayarlar kurumun VELILERINE otomatik mail gonderilmesini belirler —
 * on buro calisaninin tek basina acabilecegi bir sey degildir.
 */
@RestController
@RequestMapping("/api/bildirim-ayarlari")
@PreAuthorize("hasRole('ADMIN')")
public class BildirimAyariController {

    private final BildirimAyariService service;

    public BildirimAyariController(BildirimAyariService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<BildirimAyariResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<BildirimAyariResponse> guncelle(
            @Valid @RequestBody BildirimAyariRequest request) {
        return ApiResponse.ok(service.guncelle(request));
    }
}
