package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ogretmen olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit
 * her zaman aktif (true) baslar.
 *
 * <p>Hakedis tipine gore tutar zorunlulugu {@link HakedisTutarli} sinif duzeyi validasyonu ile;
 * eksik/gecersizse 400 VALIDATION_ERROR (error.fields.saatlikUcret / error.fields.ciroOrani).
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

        @NotNull(message = "Hakediş tipi zorunludur")
        HakedisTipi hakedisTipi,

        BigDecimal saatlikUcret,
        BigDecimal ciroOrani,

        List<Long> bransIds) implements HakedisBilgisi {
}
