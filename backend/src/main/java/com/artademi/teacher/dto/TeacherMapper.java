package com.artademi.teacher.dto;

import com.artademi.branch.Branch;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherBranch;
import com.artademi.teacher.TeacherHakedis;
import java.util.List;

/**
 * Request DTO'larini Teacher entity'sine yansitir. tenant_id ve aktif BURADA ELLE
 * yonetilmez: tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste
 * (yeni kayitta true, degisiklikte PATCH endpoint'i) yonetilir.
 *
 * <p>Brans atamasi (branchLinks) ve hakedis listesi (Model C), reconcile setter'lar uzerinden
 * uzlastirilir (mevcutlar korunur, yalnizca delta degisir). Branslar tenant-guvenli cozulmus
 * {@link Branch}'lerle baglanir (cozumleme servis katmaninda); hakedis satirlari dogrudan
 * istekten kurulur (tenant'a bagimli referans yok).
 */
public final class TeacherMapper {

    private TeacherMapper() {
    }

    /** Yeni ogretmen olusturur; aktif true ile baslar (entity varsayilani). */
    public static Teacher toNewEntity(CreateTeacherRequest req, List<Branch> branches) {
        Teacher t = Teacher.create();
        t.setAd(req.ad());
        t.setSoyad(req.soyad());
        t.setTelefon(req.telefon());
        t.setEmail(req.email());
        t.setKeycloakUserId(req.keycloakUserId());
        t.setAktif(true);
        applyBranchLinks(t, branches);
        applyHakedisler(t, req.hakedisler());
        return t;
    }

    /** Mevcut ogretmenin alanlarini gunceller; aktif'e DOKUNMAZ. Brans + hakedis atamasini yeniden kurar. */
    public static void applyUpdate(Teacher t, UpdateTeacherRequest req, List<Branch> branches) {
        t.setAd(req.ad());
        t.setSoyad(req.soyad());
        t.setTelefon(req.telefon());
        t.setEmail(req.email());
        t.setKeycloakUserId(req.keycloakUserId());
        applyBranchLinks(t, branches);
        applyHakedisler(t, req.hakedisler());
    }

    private static void applyBranchLinks(Teacher t, List<Branch> branches) {
        List<TeacherBranch> links = branches.stream()
                .map(TeacherBranch::of)
                .toList();
        t.setBranchLinks(links);
    }

    private static void applyHakedisler(Teacher t, List<HakedisSatiriRequest> rows) {
        List<TeacherHakedis> hakedisler = rows == null ? List.of() : rows.stream()
                .map(r -> TeacherHakedis.of(r.tip(), r.saatlikUcret(), r.ciroOrani(), r.dersBasiUcret()))
                .toList();
        t.setHakedisler(hakedisler);
    }
}
