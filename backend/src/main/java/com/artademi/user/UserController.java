package com.artademi.user;

import com.artademi.common.ApiResponse;
import com.artademi.user.dto.CreateUserRequest;
import com.artademi.user.dto.UpdateActiveRequest;
import com.artademi.user.dto.UpdateUserRequest;
import com.artademi.user.dto.UserResponse;
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
 * Tenant kapsamli kullanici yonetimi ucu (/api/users) — TUMU ADMIN'e ozel.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; acting admin'in tenant'i TenantContext'ten (JWT
 * {@code tenant_id}) gelir. Izolasyon ve rol kurallari {@link UserService}'te enforce edilir.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Tenant'taki kullanicilar; ?aktif=&rol=&q=&page=0&size=20 (hepsi opsiyonel). */
    @GetMapping
    public ApiResponse<List<UserResponse>> list(
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(aktif, rol, q, page, size));
    }

    /** Tek kullanici (tenant-eslesme zorunlu; baska tenant -> 404). */
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Yeni kullanici olustur (201). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Kullanici guncelle (tenant-eslesme zorunlu; baska tenant -> 404). */
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Aktiflik degisikligi; kendi hesabini pasife alamaz (400). */
    @PatchMapping("/{id}/active")
    public ApiResponse<UserResponse> changeActive(
            @PathVariable String id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return ApiResponse.ok(service.changeActive(id, request.aktif()));
    }

    /** Kullaniciyi sil; kendi hesabini silemez (400). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
