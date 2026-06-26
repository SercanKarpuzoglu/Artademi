package com.artademi.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Ogrenci grup transferi istegi. {@code yeniGrupId} zorunlu; {@code donem} opsiyonel ({@code YYYY-MM},
 * verilmezse bugunun ayi) — aidat farki tahakkuklari bu donem icin uretilir.
 */
public record TransferEnrollmentRequest(
        @NotNull(message = "Yeni grup zorunludur")
        Long yeniGrupId,

        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Dönem formatı YYYY-MM olmalı")
        String donem) {
}
