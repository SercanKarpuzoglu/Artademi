package com.artademi.inventory.dto;

import com.artademi.inventory.Product;

/**
 * Request DTO'larini Product entity'sine yansitir. tenant_id ve aktif BURADA ELLE yonetilmez:
 * tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste (yeni kayitta true,
 * degisiklikte PATCH endpoint'i) yonetilir. Stok da kendi ucundan (PATCH /stok) yonetilir; PUT
 * stoga DOKUNMAZ.
 */
public final class ProductMapper {

    private ProductMapper() {
    }

    /** Yeni urun olusturur; aktif true ile baslar. stokAdedi null gelirse 0 kabul edilir. */
    public static Product toNewEntity(CreateProductRequest req) {
        Product p = Product.create();
        p.setAd(req.ad());
        p.setSatisFiyati(req.satisFiyati());
        p.setStokAdedi(req.stokAdedi() == null ? 0 : req.stokAdedi());
        p.setAciklama(req.aciklama());
        p.setAktif(true);
        return p;
    }

    /** Mevcut urunun ad/satisFiyati/aciklama alanlarini gunceller; stok ve aktif'e DOKUNMAZ. */
    public static void applyUpdate(Product p, UpdateProductRequest req) {
        p.setAd(req.ad());
        p.setSatisFiyati(req.satisFiyati());
        p.setAciklama(req.aciklama());
    }
}
