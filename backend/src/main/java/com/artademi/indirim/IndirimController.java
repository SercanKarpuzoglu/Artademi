package com.artademi.indirim;

import com.artademi.common.ApiResponse;
import com.artademi.indirim.dto.IndirimRequest;
import com.artademi.indirim.dto.IndirimResponse;
import com.artademi.indirim.dto.OgrenciIndirimiRequest;
import com.artademi.indirim.dto.OgrenciIndirimiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indirim uclari. PARASAL: ADMIN + FRONTDESK_ACCOUNTING okur/atar; tanim yazmak (olustur/guncelle/durum)
 * YALNIZ ADMIN. On buro ve egitmen 403. Tenant JWT'den.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class IndirimController {

    private final IndirimService service;

    public IndirimController(IndirimService service) {
        this.service = service;
    }

    @GetMapping("/api/indirimler")
    public ApiResponse<List<IndirimResponse>> list(@RequestParam(required = false) Boolean aktif) {
        return ApiResponse.ok(service.list(aktif));
    }

    @PostMapping("/api/indirimler")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IndirimResponse> create(@Valid @RequestBody IndirimRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/api/indirimler/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IndirimResponse> update(@PathVariable Long id, @Valid @RequestBody IndirimRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/api/indirimler/{id}/durum")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IndirimResponse> durum(@PathVariable Long id, @RequestParam boolean aktif) {
        return ApiResponse.ok(service.durum(id, aktif));
    }

    /** Ogrencinin indirim atamalari (aktif + bitmis). */
    @GetMapping("/api/students/{ogrenciId}/indirimler")
    public ApiResponse<List<OgrenciIndirimiResponse>> ogrenciIndirimleri(@PathVariable Long ogrenciId) {
        return ApiResponse.ok(service.ogrenciIndirimleri(ogrenciId));
    }

    @PostMapping("/api/students/{ogrenciId}/indirimler")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OgrenciIndirimiResponse> ata(@PathVariable Long ogrenciId,
            @Valid @RequestBody OgrenciIndirimiRequest request) {
        return ApiResponse.ok(service.ata(ogrenciId, request));
    }

    @PatchMapping("/api/ogrenci-indirimleri/{id}/bitir")
    public ApiResponse<OgrenciIndirimiResponse> bitir(@PathVariable Long id) {
        return ApiResponse.ok(service.bitir(id));
    }
}
