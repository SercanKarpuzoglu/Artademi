package com.artademi.basvuru.dto;

import com.artademi.basvuru.Basvuru;
import com.artademi.basvuru.BasvuruDurumu;
import java.time.Instant;

/**
 * Basvuru yaniti (kurum ici — ADMIN/on buro).
 *
 * <p>{@code kaynakIp} BILINCLI olarak DISARI VERILMEZ: kotuye kullanim incelemesi icin
 * saklanir, gunluk kullanimda gosterilmesi gereksiz kisisel veri tesiridir. KVKK disa
 * aktarmasinda yer alir.
 */
public record BasvuruResponse(
        Long id,
        String ad,
        String soyad,
        String telefon,
        String email,
        String veliAdi,
        Long bransId,
        String bransAdi,
        String mesaj,
        BasvuruDurumu durum,
        Long ogrenciId,
        Instant olusturulmaTarihi) {

    public static BasvuruResponse from(Basvuru b) {
        return new BasvuruResponse(
                b.getId(),
                b.getAd(),
                b.getSoyad(),
                b.getTelefon(),
                b.getEmail(),
                b.getVeliAdi(),
                b.getBrans() != null ? b.getBrans().getId() : null,
                b.getBrans() != null ? b.getBrans().getAd() : null,
                b.getMesaj(),
                b.getDurum(),
                b.getOgrenci() != null ? b.getOgrenci().getId() : null,
                b.getOlusturulmaTarihi());
    }
}
