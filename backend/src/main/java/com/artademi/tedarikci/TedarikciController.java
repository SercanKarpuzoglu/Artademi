package com.artademi.tedarikci;

import com.artademi.common.ApiResponse;
import com.artademi.tedarikci.dto.TedarikciRequest;
import com.artademi.tedarikci.dto.TedarikciResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tedarikci yonetimi.
 *
 * <p>Yetki ADMIN + muhasebe: yanit "toplam odenen" tasidigi icin PARASALDIR.
 */
@RestController
@RequestMapping("/api/tedarikciler")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class TedarikciController {

    private final TedarikciService service;

    public TedarikciController(TedarikciService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TedarikciResponse>> list(
            @RequestParam(required = false) Boolean aktif) {
        return ApiResponse.ok(service.list(aktif));
    }

    @GetMapping("/{id}")
    public ApiResponse<TedarikciResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TedarikciResponse> create(@Valid @RequestBody TedarikciRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TedarikciResponse> update(@PathVariable Long id,
            @Valid @RequestBody TedarikciRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/durum")
    public ApiResponse<TedarikciResponse> durum(@PathVariable Long id,
            @RequestParam boolean aktif) {
        return ApiResponse.ok(service.durumDegistir(id, aktif));
    }
}
