package com.artademi.telafi.dto;

import java.time.LocalDate;

/**
 * Telafi hakki verilebilecek devamsizlik.
 *
 * <p>Bu liste olmadan yonetici "kim gelmemisti" diye yoklama kayitlarini tek tek taramak
 * zorunda kalirdi. Hak ZATEN verilmis devamsizliklar listede GORUNMEZ.
 */
public record TelafiAdayi(
        Long ogrenciId,
        String ogrenciAdSoyad,
        Long oturumId,
        LocalDate tarih,
        String grupAdi) {
}
