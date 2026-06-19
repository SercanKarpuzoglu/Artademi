package com.artademi.attendance.dto;

import com.artademi.attendance.AttendanceEntry;
import com.artademi.attendance.AttendanceSession;
import com.artademi.attendance.YoklamaDurumu;
import com.artademi.group.Group;
import com.artademi.group.GrupTipi;
import com.artademi.student.Student;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Yoklama oturumu yanit DTO'su. Entity disariya dogrudan donmez; tenant_id ASLA sizdirilmaz.
 *
 * <p>Grup ozet olarak donulur: {@code grup {id, ad, tip}}. Girisler {@link EntryView} listesi olarak
 * ogrenci ozeti ({id, ad, soyad}) + durum seklinde donulur ve ogrenci soyad, sonra ad'a gore
 * siralanir.
 *
 * <p><b>NOT:</b> alan adi {@code notu}; "not" rezerve kelimesi oldugundan kullanilmaz.
 */
public record SessionResponse(
        Long id,
        LocalDate tarih,
        String notu,
        GrupOzet grup,
        List<EntryView> entries) {

    /** Grup ozeti (id + ad + tip). */
    public record GrupOzet(Long id, String ad, GrupTipi tip) {
    }

    /** Ogrenci ozeti (id + ad + soyad). */
    public record OgrenciOzet(Long id, String ad, String soyad) {
    }

    /** Tek bir ogrencinin oturumdaki durumu. */
    public record EntryView(OgrenciOzet ogrenci, YoklamaDurumu durum) {
    }

    /** Oturum + girislerden yanit uretir. Girisler ogrenci soyad, sonra ad'a gore siralanir. */
    public static SessionResponse from(AttendanceSession session, List<AttendanceEntry> entries) {
        Group g = session.getGrup();
        GrupOzet grup = g == null ? null : new GrupOzet(g.getId(), g.getAd(), g.getTip());

        List<EntryView> views = entries.stream()
                .sorted(Comparator
                        .comparing((AttendanceEntry e) -> e.getOgrenci().getSoyad(),
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(e -> e.getOgrenci().getAd(),
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(SessionResponse::toEntryView)
                .toList();

        return new SessionResponse(
                session.getId(),
                session.getTarih(),
                session.getNotu(),
                grup,
                views);
    }

    private static EntryView toEntryView(AttendanceEntry e) {
        Student o = e.getOgrenci();
        OgrenciOzet ogrenci = new OgrenciOzet(o.getId(), o.getAd(), o.getSoyad());
        return new EntryView(ogrenci, e.getDurum());
    }
}
