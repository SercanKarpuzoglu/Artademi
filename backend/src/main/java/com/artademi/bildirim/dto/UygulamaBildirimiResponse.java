package com.artademi.bildirim.dto;

import com.artademi.bildirim.UygulamaBildirimTipi;
import com.artademi.bildirim.UygulamaBildirimi;
import java.time.Instant;
import java.util.List;

/** Zil yaniti: okunmamis sayisi + son bildirimler (en yeni ustte). */
public record UygulamaBildirimiResponse(long okunmamis, List<Satir> bildirimler) {

    public record Satir(Long id, UygulamaBildirimTipi tip, String baslik, String metin, String baglanti,
            boolean okundu, Instant olusturulmaTarihi) {
        public static Satir from(UygulamaBildirimi b) {
            return new Satir(b.getId(), b.getTip(), b.getBaslik(), b.getMetin(), b.getBaglanti(),
                    b.getOkunduTarihi() != null, b.getOlusturulmaTarihi());
        }
    }
}
