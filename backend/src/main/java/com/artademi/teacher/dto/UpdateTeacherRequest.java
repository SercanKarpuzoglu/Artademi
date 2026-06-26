package com.artademi.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Ogretmen guncelleme istegi. aktif BURADAN degismez (ona ozel PATCH endpoint var).
 *
 * <p>Model C: {@code hakedisler} ogretmenin TANIMLADIGI hakedis tipleri + oranlari (en az 1); liste
 * tutarliligi {@link HakedisTutarli} ile. Brans atamasi {@code bransIds} ile topluca yeniden kurulur
 * (serviste her id {@code findScopedById} ile tenant-guvenli dogrulanir).
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

        List<HakedisSatiriRequest> hakedisler,

        List<Long> bransIds) implements HakedisBilgisi {
}
