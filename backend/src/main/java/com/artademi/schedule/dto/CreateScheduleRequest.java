package com.artademi.schedule.dto;

import com.artademi.schedule.HaftaGunu;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Program olusturma istegi. tenant_id ve aktif ALINMAZ: tenant JWT'den gelir, yeni kayit her zaman
 * aktif (true) baslar.
 *
 * <p>{@code bitisSaati > baslangicSaati} kosulu {@link SaatAraligiGecerli} sinif duzeyi validasyonu
 * ile uygulanir; gecersizse 400 VALIDATION_ERROR (error.fields.bitisSaati).
 *
 * <p>{@code grupId} serviste {@code groupRepository.findScopedById} ile tenant-guvenli dogrulanir
 * (baska tenant'in / yok olan grup -> 404). Salon/ogretmen cakismalari serviste kontrol edilir.
 */
@SaatAraligiGecerli
public record CreateScheduleRequest(
        @NotNull(message = "Grup zorunludur")
        Long grupId,

        @NotNull(message = "Gün zorunludur")
        HaftaGunu gun,

        @NotNull(message = "Başlangıç saati zorunludur")
        LocalTime baslangicSaati,

        @NotNull(message = "Bitiş saati zorunludur")
        LocalTime bitisSaati) implements SaatBilgisi {
}
