package com.artademi.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import com.artademi.enrollment.OdemePlani;

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

        LocalDate kayitTarihi,
        /**
         * Kara listedeki ogrenci icin "yine de ekle" onayi. Onaysiz istek 409 KARA_LISTE (sebep
         * mesajda) doner; istemci popup gosterir, kullanici onaylarsa true ile tekrar gonderir.
         */
        Boolean karaListeOnayi,
        /** Dalga E: AYLIK (varsayilan) | DONEMLIK. GRUP tipinde anlamli; OZEL derste yok sayilir. */
        OdemePlani odemePlani) {
}
