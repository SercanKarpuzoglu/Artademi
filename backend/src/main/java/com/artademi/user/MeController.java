package com.artademi.user;

import com.artademi.common.ApiResponse;
import com.artademi.user.dto.ChangePasswordRequest;
import com.artademi.user.dto.MeResponse;
import com.artademi.user.dto.UpdateMeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum sahibinin kendi profili (/api/me) — her kimligi dogrulanmis role acik (rol kapisi YOK).
 * Kullanici yalnizca KENDI kaydina ({@code sub}) erisir; tenant izolasyonu dogal olarak korunur.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService service;

    public MeController(UserService service) {
        this.service = service;
    }

    /** Kendi profilim ({@code mustChangePassword} dahil). */
    @GetMapping
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(service.me());
    }

    /** Kendi profilimi guncelle (rol/tenant_id/must_change_password degismez). */
    @PutMapping
    public ApiResponse<MeResponse> updateMe(@Valid @RequestBody UpdateMeRequest request) {
        return ApiResponse.ok(service.updateMe(request));
    }

    /** Parolami degistir (mevcut parola dogrulanir; ilk-parola akisini kapatir). */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(request);
        return ApiResponse.ok(null);
    }
}
