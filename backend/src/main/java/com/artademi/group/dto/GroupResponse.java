package com.artademi.group.dto;

import com.artademi.group.Group;
import com.artademi.group.GrupTipi;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Grup yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Referanslar tam DTO yerine ozet olarak donulur: brans/salon {id, ad}, ogretmen
 * {id, ad, soyad}. Salon OZEL grupta null olabilir. Bu ozetler entity @ManyToOne'larindan
 * map'lenir; ilgili entity'ler tenant-filtreli yuklenir (defense-in-depth).
 */
public record GroupResponse(
        Long id,
        String ad,
        GrupTipi tip,
        BranchRef brans,
        TeacherRef ogretmen,
        RoomRef salon,
        String seviye,
        BigDecimal aylikAidat,
        BigDecimal dersBasiUcret,
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Brans ozeti (id + ad). */
    public record BranchRef(Long id, String ad) {
    }

    /** Salon ozeti (id + ad). */
    public record RoomRef(Long id, String ad) {
    }

    /** Ogretmen ozeti (id + ad + soyad). */
    public record TeacherRef(Long id, String ad, String soyad) {
    }

    public static GroupResponse from(Group g) {
        BranchRef brans = g.getBrans() == null
                ? null
                : new BranchRef(g.getBrans().getId(), g.getBrans().getAd());
        TeacherRef ogretmen = g.getOgretmen() == null
                ? null
                : new TeacherRef(g.getOgretmen().getId(), g.getOgretmen().getAd(), g.getOgretmen().getSoyad());
        RoomRef salon = g.getSalon() == null
                ? null
                : new RoomRef(g.getSalon().getId(), g.getSalon().getAd());
        return new GroupResponse(
                g.getId(),
                g.getAd(),
                g.getTip(),
                brans,
                ogretmen,
                salon,
                g.getSeviye(),
                g.getAylikAidat(),
                g.getDersBasiUcret(),
                g.isAktif(),
                g.getOlusturulmaTarihi(),
                g.getGuncellenmeTarihi());
    }
}
