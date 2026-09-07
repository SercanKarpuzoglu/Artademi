package com.artademi.paket;

import com.artademi.common.ApiResponse;
import com.artademi.paket.dto.PaketResponse;
import com.artademi.paket.dto.PaketSatRequest;
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
 * Ders paketi (kontor) satisi.
 *
 * <p>Yetki ADMIN + muhasebe: paket satisi tahakkuk uretir, yani PARASAL bir islemdir.
 */
@RestController
@RequestMapping("/api/paketler")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class PaketController {

    private final PaketService service;

    public PaketController(PaketService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<PaketResponse>> list(@RequestParam(required = false) Long ogrenciId) {
        return ApiResponse.ok(service.list(ogrenciId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaketResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Paket satar; PESIN tek tahakkuk uretir. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaketResponse> sat(@Valid @RequestBody PaketSatRequest request) {
        return ApiResponse.ok(service.sat(request));
    }

    /** Paketi iptal eder. Tahakkuk OTOMATIK silinmez (tahsilat yapilmis olabilir). */
    @PostMapping("/{id}/iptal")
    public ApiResponse<PaketResponse> iptal(@PathVariable Long id) {
        return ApiResponse.ok(service.iptal(id));
    }
}
