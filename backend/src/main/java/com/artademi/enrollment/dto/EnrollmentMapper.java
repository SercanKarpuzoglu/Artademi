package com.artademi.enrollment.dto;

import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentDurumu;
import com.artademi.group.Group;
import com.artademi.student.Student;
import java.time.LocalDate;

/**
 * Request DTO'sunu Enrollment entity'sine yansitir. tenant_id ve durum BURADA ELLE yonetilmez:
 * tenant @PrePersist'te TenantContext'ten gelir, durum yeni kayitta AKTIF baslar.
 *
 * <p>ogrenci/grup, tenant-guvenli cozulmus ({@code findScopedById}) entity'lerle baglanir;
 * cozumleme servis katmaninda yapilir, mapper yalnizca set eder.
 */
public final class EnrollmentMapper {

    private EnrollmentMapper() {
    }

    /** Yeni kayit olusturur; durum AKTIF ile baslar. kayitTarihi null ise bugun kullanilir. */
    public static Enrollment toNewEntity(Student ogrenci, Group grup, LocalDate kayitTarihi) {
        Enrollment e = Enrollment.create();
        e.setOgrenci(ogrenci);
        e.setGrup(grup);
        e.setKayitTarihi(kayitTarihi != null ? kayitTarihi : LocalDate.now());
        e.setDurum(EnrollmentDurumu.AKTIF);
        return e;
    }
}
