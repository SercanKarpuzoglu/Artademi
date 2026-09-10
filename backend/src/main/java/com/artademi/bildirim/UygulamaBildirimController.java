package com.artademi.bildirim;

import com.artademi.bildirim.dto.UygulamaBildirimiResponse;
import com.artademi.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zil uclari — tum is rolleri (egitmen dahil; kendi bildirimlerini gorur). Tenant JWT'den.
 * Web 30 sn'de bir GET /api/bildirimler cagirir; yeni id gorunce toast gosterir.
 */
@RestController
@RequestMapping("/api/bildirimler")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING','TEACHER')")
public class UygulamaBildirimController {

    private final UygulamaBildirimService service;

    public UygulamaBildirimController(UygulamaBildirimService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<UygulamaBildirimiResponse> zil() {
        return ApiResponse.ok(service.zil());
    }

    @PostMapping("/{id}/okundu")
    public ApiResponse<Void> okundu(@PathVariable Long id) {
        service.okundu(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/okundu-hepsi")
    public ApiResponse<Integer> hepsiniOkundu() {
        return ApiResponse.ok(service.hepsiniOkundu());
    }
}
