package com.artademi.branch.dto;

import com.artademi.branch.Branch;
import java.time.Instant;

/**
 * Brans yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 */
public record BranchResponse(
        Long id,
        String ad,
        String aciklama,
        /** Bransin varsayilan donemi (yoksa null) — yeni grup formunda on-doldurma icin. */
        DonemRef donem,
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Donem ozeti (id + ad). */
    public record DonemRef(Long id, String ad) {
    }

    public static BranchResponse from(Branch b) {
        return new BranchResponse(
                b.getId(),
                b.getAd(),
                b.getAciklama(),
                b.getDonem() == null ? null : new DonemRef(b.getDonem().getId(), b.getDonem().getAd()),
                b.isAktif(),
                b.getOlusturulmaTarihi(),
                b.getGuncellenmeTarihi());
    }
}
