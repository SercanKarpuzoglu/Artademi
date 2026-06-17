package com.artademi.room.dto;

import com.artademi.room.Room;
import java.time.Instant;

/**
 * Salon yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 */
public record RoomResponse(
        Long id,
        String ad,
        Integer kapasite,
        String aciklama,
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    public static RoomResponse from(Room r) {
        return new RoomResponse(
                r.getId(),
                r.getAd(),
                r.getKapasite(),
                r.getAciklama(),
                r.isAktif(),
                r.getOlusturulmaTarihi(),
                r.getGuncellenmeTarihi());
    }
}
