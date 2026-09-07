package com.artademi.paket.dto;

import com.artademi.paket.DersPaketi;
import com.artademi.paket.PaketDurumu;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ders paketi yaniti.
 *
 * @param kullanilanDers tuketim satiri sayisi — HESAPLANIR, saklanmaz
 * @param kalanDers toplam - kullanilan; negatif olamaz
 * @param suresiDoldu HESAPLANIR
 */
public record PaketResponse(
        Long id,
        Long ogrenciId,
        String ogrenciAdSoyad,
        String ad,
        Long grupId,
        String grupAdi,
        int toplamDers,
        long kullanilanDers,
        long kalanDers,
        BigDecimal tutar,
        LocalDate satisTarihi,
        LocalDate sonKullanmaTarihi,
        boolean suresiDoldu,
        PaketDurumu durum,
        Long accrualId,
        String aciklama) {

    public static PaketResponse from(DersPaketi p, long kullanilan, LocalDate bugun) {
        return new PaketResponse(
                p.getId(),
                p.getOgrenci().getId(),
                (p.getOgrenci().getAd() + " " + p.getOgrenci().getSoyad()).trim(),
                p.getAd(),
                p.getGrup() != null ? p.getGrup().getId() : null,
                p.getGrup() != null ? p.getGrup().getAd() : null,
                p.getToplamDers(),
                kullanilan,
                Math.max(0, p.getToplamDers() - kullanilan),
                p.getTutar(),
                p.getSatisTarihi(),
                p.getSonKullanmaTarihi(),
                p.suresiDolduMu(bugun),
                p.getDurum(),
                p.getAccrual() != null ? p.getAccrual().getId() : null,
                p.getAciklama());
    }
}
