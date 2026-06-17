package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ogretmen guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 *
 * <p>Hakedis tutarliligi {@link HakedisTutarli} ile; brans atamasi {@code bransIds} ile
 * topluca yeniden kurulur (serviste her id {@code findScopedById} ile tenant-guvenli dogrulanir).
 */
@HakedisTutarli
public record UpdateTeacherRequest(
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
