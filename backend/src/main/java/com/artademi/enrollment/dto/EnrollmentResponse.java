package com.artademi.enrollment.dto;

import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentDurumu;
import com.artademi.group.GrupTipi;
import com.artademi.student.StudentStatus;
import java.time.LocalDate;

/**
 * Kayit yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>Referanslar tam DTO yerine ozet olarak donulur: ogrenci {id, ad, soyad, status}, grup
 * {id, ad, tip}. Ogrenci statusu grup ekraninda DENEME uyarisi icin tasinir (aidat uretilmez).
 * Bu ozetler entity @ManyToOne'larindan map'lenir; ilgili entity'ler tenant-filtreli yuklenir
 * (defense-in-depth).
 */
public record EnrollmentResponse(
        Long id,
        EnrollmentDurumu durum,
        LocalDate kayitTarihi,
        LocalDate ayrilmaTarihi,
        OgrenciRef ogrenci,
        GrupRef grup) {

    /** Ogrenci ozeti (id + ad + soyad + status). */
    public record OgrenciRef(Long id, String ad, String soyad, StudentStatus status) {
    }

    /** Grup ozeti (id + ad + tip). */
    public record GrupRef(Long id, String ad, GrupTipi tip) {
    }

    public static EnrollmentResponse from(Enrollment e) {
        OgrenciRef ogrenci = e.getOgrenci() == null
                ? null
                : new OgrenciRef(e.getOgrenci().getId(), e.getOgrenci().getAd(), e.getOgrenci().getSoyad(),
                        e.getOgrenci().getStatus());
        GrupRef grup = e.getGrup() == null
                ? null
                : new GrupRef(e.getGrup().getId(), e.getGrup().getAd(), e.getGrup().getTip());
        return new EnrollmentResponse(
                e.getId(),
                e.getDurum(),
                e.getKayitTarihi(),
                e.getAyrilmaTarihi(),
                ogrenci,
                grup);
    }
}
