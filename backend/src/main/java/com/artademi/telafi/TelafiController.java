package com.artademi.telafi;

import com.artademi.common.ApiResponse;
import com.artademi.telafi.dto.TelafiAdayi;
import com.artademi.telafi.dto.TelafiKullanRequest;
import com.artademi.telafi.dto.TelafiResponse;
import com.artademi.telafi.dto.TelafiVerRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telafi ders hakki.
 *
 * <p>Yetki ofis rolleri: telafi takibi ON BURO isidir ve hicbir parasal bilgi tasimaz.
 */
@RestController
@RequestMapping("/api/telafi")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class TelafiController {

    private final TelafiService service;

    public TelafiController(TelafiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TelafiResponse>> list(
            @RequestParam(required = false) TelafiDurumu durum,
            @RequestParam(required = false) Long ogrenciId) {
        return ApiResponse.ok(service.list(durum, ogrenciId));
    }

    /** Panel rozeti: kullanilmayi bekleyen hak sayisi. */
    @GetMapping("/bekleyen-sayisi")
    public ApiResponse<Long> bekleyenSayisi() {
        return ApiResponse.ok(service.bekleyenSayisi());
    }

    /** Hak verilebilecek devamsizliklar (oneri listesi; hak OTOMATIK dogmaz). */
    @GetMapping("/adaylar")
    public ApiResponse<List<TelafiAdayi>> adaylar() {
        return ApiResponse.ok(service.adaylar());
    }

    @GetMapping("/{id}")
    public ApiResponse<TelafiResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TelafiResponse> ver(@Valid @RequestBody TelafiVerRequest request) {
        return ApiResponse.ok(service.ver(request));
    }

    @PostMapping("/{id}/kullan")
    public ApiResponse<TelafiResponse> kullan(@PathVariable Long id,
            @Valid @RequestBody TelafiKullanRequest request) {
        return ApiResponse.ok(service.kullan(id, request));
    }

    @PostMapping("/{id}/iptal")
    public ApiResponse<TelafiResponse> iptal(@PathVariable Long id) {
        return ApiResponse.ok(service.iptal(id));
    }
}
