package com.artademi.silme;

import java.time.Instant;

/** "Silinenler" listesi satiri: geri alma icin. */
public record SilinenKayit(String tur, Long id, String ad, Instant silindiTarihi, String silen) {
}
