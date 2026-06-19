package com.artademi.schedule;

/**
 * Haftanın günü; bir grubun haftalık ders saatinin hangi güne ait olduğunu belirler.
 *
 * <p>Sıralama (ordinal) PAZARTESI -> PAZAR şeklindedir; listeler bu enum sırasına göre
 * (gün, ardından başlangıç saati) sıralanır.
 */
public enum HaftaGunu {
    PAZARTESI,
    SALI,
    CARSAMBA,
    PERSEMBE,
    CUMA,
    CUMARTESI,
    PAZAR
}
