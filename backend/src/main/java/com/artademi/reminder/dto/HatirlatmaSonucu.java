package com.artademi.reminder.dto;

import java.util.List;

/**
 * Toplu gonderim sonucu. Kismi basari NORMALDIR (biri mailsiz, biri yakinda gonderilmis) —
 * bu yuzden tek bir "basarili" bayragi yerine ogrenci bazinda sonuc doner.
 */
public record HatirlatmaSonucu(int gonderilen, int atlanan, List<Satir> satirlar) {

    public record Satir(Long ogrenciId, String adSoyad, boolean gonderildi, String aciklama) {
    }
}
