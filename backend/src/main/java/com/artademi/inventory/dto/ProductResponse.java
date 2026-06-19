package com.artademi.inventory.dto;

import com.artademi.inventory.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Urun yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 */
public record ProductResponse(
        Long id,
        String ad,
        BigDecimal satisFiyati,
        int stokAdedi,
        String aciklama,
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getAd(),
                p.getSatisFiyati(),
                p.getStokAdedi(),
                p.getAciklama(),
                p.isAktif(),
                p.getOlusturulmaTarihi(),
                p.getGuncellenmeTarihi());
    }
}
