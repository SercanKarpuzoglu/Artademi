package com.artademi.user.dto;

import java.util.List;

/**
 * Oturum sahibinin kendi profili (/api/me GET). {@code mustChangePassword} ilk-parola akisi
 * icindir; frontend bu bayrak true iken kullaniciyi parola degistirmeye yonlendirir.
 *
 * <p>{@code tenantId}/{@code tenantAdi}: oturum sahibinin tenant'i (topbar'da gosterim icin; ayri
 * cagri gerekmesin diye burada doner). Tenant adi yoksa null.
 */
public record MeResponse(
        String sub,
        String kullaniciAdi,
        String ad,
        String soyad,
        String email,
        String telefon,
        List<String> roller,
        boolean mustChangePassword,
        String tenantId,
        String tenantAdi) {
}
