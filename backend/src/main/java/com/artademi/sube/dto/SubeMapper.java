package com.artademi.sube.dto;

import com.artademi.sube.Sube;

/**
 * Request DTO'larini Sube entity'sine yansitir. tenant_id ve aktif BURADA ELLE yonetilmez:
 * tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste yonetilir.
 */
public final class SubeMapper {

    private SubeMapper() {
    }

    /** Yeni sube olusturur; aktif true ile baslar (entity varsayilani). */
    public static Sube toNewEntity(CreateSubeRequest req) {
        Sube s = Sube.create();
        s.setAd(req.ad());
        s.setAdres(req.adres());
        s.setTelefon(req.telefon());
        s.setAktif(true);
        return s;
    }

    /** Mevcut subenin alanlarini gunceller; aktif'e DOKUNMAZ. */
    public static void applyUpdate(Sube s, UpdateSubeRequest req) {
        s.setAd(req.ad());
        s.setAdres(req.adres());
        s.setTelefon(req.telefon());
    }
}
