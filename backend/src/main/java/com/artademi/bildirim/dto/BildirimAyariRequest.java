package com.artademi.bildirim.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Bildirim tercihlerini gunceller.
 *
 * @param haftalikOzetGunu ISO-8601: 1=Pazartesi … 7=Pazar
 */
public record BildirimAyariRequest(
        boolean borcHatirlatmaOtomatik,
        boolean devamsizlikBildirimi,
        boolean haftalikOzet,
        @Min(value = 1, message = "Gün 1-7 arasında olmalıdır")
        @Max(value = 7, message = "Gün 1-7 arasında olmalıdır")
        short haftalikOzetGunu) {
}
