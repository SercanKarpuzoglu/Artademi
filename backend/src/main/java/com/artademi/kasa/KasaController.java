package com.artademi.kasa;

import com.artademi.common.ApiResponse;
import com.artademi.kasa.dto.DuzeltmeRequest;
import com.artademi.kasa.dto.HareketResponse;
import com.artademi.kasa.dto.KasaRequest;
import com.artademi.kasa.dto.KasaResponse;
import com.artademi.kasa.dto.TransferRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kasa yonetimi.
 *
 * <p>Yetki ADMIN + muhasebe: kasa bakiyesi PARASAL bilgidir, on buro gormez.
 */
@RestController
@RequestMapping("/api/kasalar")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class KasaController {

    private final KasaService service;

    public KasaController(KasaService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<KasaResponse>> list(@RequestParam(required = false) Boolean aktif) {
        return ApiResponse.ok(service.list(aktif));
    }

    @GetMapping("/{id}")
    public ApiResponse<KasaResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KasaResponse> create(@Valid @RequestBody KasaRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<KasaResponse> update(@PathVariable Long id,
            @Valid @RequestBody KasaRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/durum")
    public ApiResponse<KasaResponse> durum(@PathVariable Long id, @RequestParam boolean aktif) {
        return ApiResponse.ok(service.durumDegistir(id, aktif));
    }

    @GetMapping("/{id}/hareketler")
    public ApiResponse<List<HareketResponse>> hareketler(@PathVariable Long id) {
        return ApiResponse.ok(service.hareketler(id));
    }

    /** Kasalar arasi transfer — iki hareket satiri doner (cikis + giris). */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<HareketResponse>> transfer(@Valid @RequestBody TransferRequest req) {
        return ApiResponse.ok(service.transfer(req));
    }

    @PostMapping("/{id}/duzeltme")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HareketResponse> duzeltme(@PathVariable Long id,
            @Valid @RequestBody DuzeltmeRequest req) {
        return ApiResponse.ok(service.duzeltme(id, req));
    }

    /** Transferse IKI bacak birden silinir; yarim transfer kalmaz. */
    @DeleteMapping("/hareketler/{hareketId}")
    public ApiResponse<Void> hareketSil(@PathVariable Long hareketId) {
        service.hareketSil(hareketId);
        return ApiResponse.ok(null);
    }
}
