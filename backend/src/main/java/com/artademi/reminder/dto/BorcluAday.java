package com.artademi.reminder.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hatirlatma gonderilebilecek borclu ogrenci.
 *
 * @param veliMail null ise mail GONDERILEMEZ; arayuz bunu acikca gosterir (sessizce atlanmaz)
 * @param sonHatirlatma daha once gonderildiyse tarihi; {@code gonderilebilir=false} ise sebebi budur
 */
public record BorcluAday(
        Long ogrenciId,
        String adSoyad,
        BigDecimal bakiye,
        String veliMail,
        Instant sonHatirlatma,
        boolean gonderilebilir,
        String engelSebebi) {
}
