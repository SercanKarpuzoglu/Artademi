package com.artademi.user.dto;

import com.artademi.platform.dto.SubscriptionWarning;
import java.util.List;

/**
 * Oturum sahibinin kendi profili (/api/me GET). {@code mustChangePassword} ilk-parola akisi
 * icindir; frontend bu bayrak true iken kullaniciyi parola degistirmeye yonlendirir.
 *
 * <p>{@code tenantId}/{@code tenantAdi}: oturum sahibinin tenant'i (topbar'da gosterim icin; ayri
 * cagri gerekmesin diye burada doner). Tenant adi yoksa null.
 *
 * <p>{@code subscriptionWarning}: tenant aboneligi grace'te ({@code ODEME_BEKLIYOR}) ise dolu olur
 * (frontend banner yapar); aksi halde null. Odeme/abonelik mantigi platform paketindedir.
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
        String tenantAdi,
        SubscriptionWarning subscriptionWarning) {
}
