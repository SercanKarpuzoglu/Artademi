package com.artademi.room.dto;

import com.artademi.room.Room;

/**
 * Request DTO'larini Room entity'sine yansitir. tenant_id ve aktif BURADA ELLE
 * yonetilmez: tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste
 * (yeni kayitta true, degisiklikte PATCH endpoint'i) yonetilir.
 */
public final class RoomMapper {

    private RoomMapper() {
    }

    /** Yeni salon olusturur; aktif true ile baslar (entity varsayilani). */
    public static Room toNewEntity(CreateRoomRequest req) {
        Room r = Room.create();
        r.setAd(req.ad());
        r.setKapasite(req.kapasite());
        r.setAciklama(req.aciklama());
        r.setAktif(true);
        return r;
    }

    /** Mevcut salonun alanlarini gunceller; aktif'e DOKUNMAZ. */
    public static void applyUpdate(Room r, UpdateRoomRequest req) {
        r.setAd(req.ad());
        r.setKapasite(req.kapasite());
        r.setAciklama(req.aciklama());
    }
}
