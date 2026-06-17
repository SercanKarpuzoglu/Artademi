package com.artademi.group.dto;

import com.artademi.group.GrupTipi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Grup olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit her zaman
 * aktif (true) baslar.
 *
 * <p>Tipe gore salon/ucret zorunlulugu {@link GrupTutarli} sinif duzeyi validasyonu ile;
 * eksik/gecersizse 400 VALIDATION_ERROR (error.fields.salonId / aylikAidat / dersBasiUcret).
 *
 * <p>{@code bransId}, {@code ogretmenId} (zorunlu) ve {@code salonId} (opsiyonel) serviste
 * {@code findScopedById} ile tenant-guvenli dogrulanir (baska tenant'in / yok olan referans -> 404).
 */
@GrupTutarli
public record CreateGroupRequest(
        @NotBlank(message = "Ad zorunludur")
        String ad,

        @NotNull(message = "Tip zorunludur")
        GrupTipi tip,

        @NotNull(message = "Branş zorunludur")
        Long bransId,

        @NotNull(message = "Öğretmen zorunludur")
        Long ogretmenId,

        Long salonId,
        String seviye,
        BigDecimal aylikAidat,
        BigDecimal dersBasiUcret) implements GrupBilgisi {
}
