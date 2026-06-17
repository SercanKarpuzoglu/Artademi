package com.artademi.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Kayit olusturma istegi. tenant_id ve durum ALINMAZ: tenant JWT'den gelir, yeni kayit her zaman
 * AKTIF baslar.
 *
 * <p>{@code ogrenciId} ve {@code grupId} (zorunlu) serviste {@code findScopedById} ile tenant-guvenli
 * dogrulanir (baska tenant'in / yok olan referans -> 404).
 *
 * <p>{@code kayitTarihi} opsiyonel; verilmezse serviste bugun (LocalDate.now()) kullanilir.
 */
public record CreateEnrollmentRequest(
        @NotNull(message = "Öğrenci zorunludur")
        Long ogrenciId,

        @NotNull(message = "Grup zorunludur")
        Long grupId,

        LocalDate kayitTarihi) {
}
