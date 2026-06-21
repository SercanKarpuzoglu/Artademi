package com.artademi.platform.dto;

import java.time.LocalDate;

/**
 * Iş kullanicisina gosterilecek abonelik uyarisi ({@code /api/me} icinde). Yalnizca abonelik
 * {@code ODEME_BEKLIYOR} (grace) iken dolu olur; aksi halde null doner. Frontend bunu banner yapar
 * (bu pakette frontend YOK — yalnizca backend bayragi).
 */
public record SubscriptionWarning(
        boolean inGrace,
        LocalDate graceEndsAt,
        String message) {
}
