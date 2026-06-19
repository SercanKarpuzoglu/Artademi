package com.artademi.attendance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Yoklama oturumu olusturma istegi. tenant_id ALINMAZ: tenant JWT'den gelir.
 *
 * <p>{@code grupId} (zorunlu) serviste {@code groupRepository.findScopedById} ile tenant-guvenli
 * dogrulanir (baska tenant'in / yok olan grup -> 404). {@code programId} opsiyonel; verilirse
 * tenant-guvenli cozulur ve grubuna ait olmak zorundadir (degilse 400). {@code notu} opsiyonel.
 *
 * <p><b>NOT:</b> alan adi {@code notu}; "not" Java/SQL rezerve kelimesi oldugundan kullanilmaz.
 */
public record CreateSessionRequest(
        @NotNull(message = "Grup zorunludur")
        Long grupId,

        @NotNull(message = "Tarih zorunludur")
        LocalDate tarih,

        Long programId,

        String notu) {
}
