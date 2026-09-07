package com.artademi.telafi.dto;

import com.artademi.telafi.TelafiDurumu;
import com.artademi.telafi.TelafiHakki;
import java.time.LocalDate;

/**
 * Telafi hakki yaniti.
 *
 * @param suresiDoldu HESAPLANMIS — saklanmaz. Yalnizca BEKLIYOR haklar icin anlamlidir.
 */
public record TelafiResponse(
        Long id,
        Long ogrenciId,
        String ogrenciAdSoyad,
        Long kaynakOturumId,
        LocalDate kaynakDers,
        String kaynakGrup,
        LocalDate verilmeTarihi,
        LocalDate sonKullanmaTarihi,
        TelafiDurumu durum,
        boolean suresiDoldu,
        Long kullanilanOturumId,
        LocalDate kullanimTarihi,
        String aciklama) {

    public static TelafiResponse from(TelafiHakki t, LocalDate bugun) {
        var kaynak = t.getKaynakOturum();
        return new TelafiResponse(
                t.getId(),
                t.getOgrenci().getId(),
                (t.getOgrenci().getAd() + " " + t.getOgrenci().getSoyad()).trim(),
                kaynak != null ? kaynak.getId() : null,
                kaynak != null ? kaynak.getTarih() : null,
                kaynak != null && kaynak.getGrup() != null ? kaynak.getGrup().getAd() : null,
                t.getVerilmeTarihi(),
                t.getSonKullanmaTarihi(),
                t.getDurum(),
                t.suresiDolduMu(bugun),
                t.getKullanilanOturum() != null ? t.getKullanilanOturum().getId() : null,
                t.getKullanimTarihi(),
                t.getAciklama());
    }
}
