package com.artademi.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Ogretmen olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit
 * her zaman aktif (true) baslar.
 *
 * <p>Model C: {@code hakedisler} ogretmenin TANIMLADIGI hakedis tipleri + oranlari (en az 1).
 * Liste tutarliligi {@link HakedisTutarli} sinif duzeyi validasyonu ile; eksik/gecersizse 400
 * VALIDATION_ERROR (error.fields.hakedisler / hakedisler[i].saatlikUcret vb.).
 *
 * <p>{@code bransIds}: atanacak brans id'leri; her biri serviste {@code findScopedById} ile
 * tenant-guvenli dogrulanir (baska tenant'in / yok olan branş -> 404).
 */
@HakedisTutarli
public record CreateTeacherRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotBlank(message = "Soyad zorunludur")
        String soyad,

        String telefon,
        String email,
        String keycloakUserId,

        List<HakedisSatiriRequest> hakedisler,

        List<Long> bransIds) implements HakedisBilgisi {
}
