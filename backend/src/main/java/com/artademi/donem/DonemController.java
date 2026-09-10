package com.artademi.donem;

import com.artademi.common.ApiResponse;
import com.artademi.donem.dto.DonemRequest;
import com.artademi.donem.dto.DonemResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Donem tanimlari: okuma ofis rolleri, yazma ADMIN. Tenant JWT'den. */
@RestController
@RequestMapping("/api/donemler")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class DonemController {

    private final DonemService service;

    public DonemController(DonemService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DonemResponse>> list(@RequestParam(required = false) Boolean aktif) {
        return ApiResponse.ok(service.list(aktif));
    }

    @GetMapping("/{id}")
    public ApiResponse<DonemResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DonemResponse> create(@Valid @RequestBody DonemRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DonemResponse> update(@PathVariable Long id, @Valid @RequestBody DonemRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DonemResponse> changeActive(@PathVariable Long id, @RequestParam boolean aktif) {
        return ApiResponse.ok(service.changeActive(id, aktif));
    }
}
