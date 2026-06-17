package com.artademi.teacher.dto;

import com.artademi.branch.Branch;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherBranch;
import java.util.List;

/**
 * Request DTO'larini Teacher entity'sine yansitir. tenant_id ve aktif BURADA ELLE
 * yonetilmez: tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste
 * (yeni kayitta true, degisiklikte PATCH endpoint'i) yonetilir.
 *
 * <p>Brans atamasi (branchLinks), tenant-guvenli cozulmus {@link Branch}'lerle yapilir;
 * cozumleme (findScopedById ile) servis katmaninda yapilir, mapper yalnizca baglar.
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
        t.setHakedisTipi(req.hakedisTipi());
        t.setSaatlikUcret(req.saatlikUcret());
        t.setCiroOrani(req.ciroOrani());
        t.setAktif(true);
        applyBranchLinks(t, branches);
        return t;
    }

    /** Mevcut ogretmenin alanlarini gunceller; aktif'e DOKUNMAZ. Brans atamasini yeniden kurar. */
    public static void applyUpdate(Teacher t, UpdateTeacherRequest req, List<Branch> branches) {
        t.setAd(req.ad());
        t.setSoyad(req.soyad());
        t.setTelefon(req.telefon());
        t.setEmail(req.email());
        t.setKeycloakUserId(req.keycloakUserId());
        t.setHakedisTipi(req.hakedisTipi());
        t.setSaatlikUcret(req.saatlikUcret());
        t.setCiroOrani(req.ciroOrani());
        applyBranchLinks(t, branches);
    }

    private static void applyBranchLinks(Teacher t, List<Branch> branches) {
        List<TeacherBranch> links = branches.stream()
                .map(TeacherBranch::of)
                .toList();
        t.setBranchLinks(links);
    }
}
