package com.artademi.schedule.dto;

import com.artademi.group.Group;
import com.artademi.group.GrupTipi;
import com.artademi.schedule.HaftaGunu;
import com.artademi.schedule.Schedule;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Program yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Grup tam DTO yerine ozet olarak donulur: {@code grup {id, ad, tip}}. Ayrica is gerekligine
 * gore grubun salon {id, ad} (OZEL/salonsuz grupta null) ve ogretmen {id, ad, soyad} ozetleri de
 * donulur; bu ozetler entity'nin @ManyToOne grup -> salon/ogretmen iliskisinden map'lenir ve
 * ilgili entity'ler tenant-filtreli yuklenir (defense-in-depth).
 */
public record ScheduleResponse(
        Long id,
        HaftaGunu gun,
        LocalTime baslangicSaati,
        LocalTime bitisSaati,
        boolean aktif,
        GrupOzet grup,
        RoomRef salon,
        TeacherRef ogretmen,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Grup ozeti (id + ad + tip). */
    public record GrupOzet(Long id, String ad, GrupTipi tip) {
    }

    /** Salon ozeti (id + ad). */
    public record RoomRef(Long id, String ad) {
    }

    /** Ogretmen ozeti (id + ad + soyad). */
    public record TeacherRef(Long id, String ad, String soyad) {
    }

    public static ScheduleResponse from(Schedule s) {
        Group g = s.getGrup();
        GrupOzet grup = g == null ? null : new GrupOzet(g.getId(), g.getAd(), g.getTip());
        RoomRef salon = (g == null || g.getSalon() == null)
                ? null
                : new RoomRef(g.getSalon().getId(), g.getSalon().getAd());
        TeacherRef ogretmen = (g == null || g.getOgretmen() == null)
                ? null
                : new TeacherRef(g.getOgretmen().getId(), g.getOgretmen().getAd(), g.getOgretmen().getSoyad());
        return new ScheduleResponse(
                s.getId(),
                s.getGun(),
                s.getBaslangicSaati(),
                s.getBitisSaati(),
                s.isAktif(),
                grup,
                salon,
                ogretmen,
                s.getOlusturulmaTarihi(),
                s.getGuncellenmeTarihi());
    }
}
