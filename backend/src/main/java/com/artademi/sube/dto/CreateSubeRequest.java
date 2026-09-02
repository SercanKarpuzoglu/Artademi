package com.artademi.sube.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sube olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit
 * her zaman aktif (true) baslar.
 */
public record CreateSubeRequest(
        @NotBlank(message = "Ad zorunludur")
        @Size(max = 150, message = "Ad en fazla 150 karakter olabilir")
        String ad,

        @Size(max = 500, message = "Adres en fazla 500 karakter olabilir")
        String adres,

        @Size(max = 30, message = "Telefon en fazla 30 karakter olabilir")
        String telefon) {
}
