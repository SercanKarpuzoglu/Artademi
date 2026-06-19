package com.artademi.schedule.dto;

import com.artademi.group.Group;
import com.artademi.schedule.Schedule;

/**
 * Request DTO'larini Schedule entity'sine yansitir. tenant_id ve aktif BURADA ELLE yonetilmez:
 * tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste (yeni kayitta true,
 * degisiklikte PATCH endpoint'i) yonetilir.
 *
 * <p>grup, tenant-guvenli cozulmus ({@code groupRepository.findScopedById}) entity ile baglanir;
 * cozumleme servis katmaninda yapilir, mapper yalnizca set eder.
 */
public final class ScheduleMapper {

    private ScheduleMapper() {
    }

    /** Yeni program olusturur; aktif true ile baslar (entity varsayilani). */
    public static Schedule toNewEntity(CreateScheduleRequest req, Group grup) {
        Schedule s = Schedule.create();
        s.setGrup(grup);
        s.setGun(req.gun());
        s.setBaslangicSaati(req.baslangicSaati());
        s.setBitisSaati(req.bitisSaati());
        s.setAktif(true);
        return s;
    }

    /** Mevcut programin alanlarini gunceller; aktif'e DOKUNMAZ. */
    public static void applyUpdate(Schedule s, UpdateScheduleRequest req, Group grup) {
        s.setGrup(grup);
        s.setGun(req.gun());
        s.setBaslangicSaati(req.baslangicSaati());
        s.setBitisSaati(req.bitisSaati());
    }
}
