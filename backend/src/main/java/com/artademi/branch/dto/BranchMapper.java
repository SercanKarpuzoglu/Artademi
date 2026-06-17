package com.artademi.branch.dto;

import com.artademi.branch.Branch;

/**
 * Request DTO'larini Branch entity'sine yansitir. tenant_id ve aktif BURADA ELLE
 * yonetilmez: tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste
 * (yeni kayitta true, degisiklikte PATCH endpoint'i) yonetilir.
 */
public final class BranchMapper {

    private BranchMapper() {
    }

    /** Yeni brans olusturur; aktif true ile baslar (entity varsayilani). */
    public static Branch toNewEntity(CreateBranchRequest req) {
        Branch b = Branch.create();
        b.setAd(req.ad());
        b.setAciklama(req.aciklama());
        b.setAktif(true);
        return b;
    }

    /** Mevcut bransin alanlarini gunceller; aktif'e DOKUNMAZ. */
    public static void applyUpdate(Branch b, UpdateBranchRequest req) {
        b.setAd(req.ad());
        b.setAciklama(req.aciklama());
    }
}
