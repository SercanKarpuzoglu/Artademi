package com.artademi.sube.dto;

import com.artademi.sube.Sube;
import java.time.Instant;

/** Sube yanit DTO'su. Entity disariya dogrudan donmez; tenant_id sizdirilmaz. */
public record SubeResponse(
        Long id,
        String ad,
        String adres,
        String telefon,
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    public static SubeResponse from(Sube s) {
        return new SubeResponse(
                s.getId(),
                s.getAd(),
                s.getAdres(),
                s.getTelefon(),
                s.isAktif(),
                s.getOlusturulmaTarihi(),
                s.getGuncellenmeTarihi());
    }
}
