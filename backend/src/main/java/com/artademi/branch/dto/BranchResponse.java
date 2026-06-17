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
        boolean aktif,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    public static BranchResponse from(Branch b) {
        return new BranchResponse(
                b.getId(),
                b.getAd(),
                b.getAciklama(),
                b.isAktif(),
                b.getOlusturulmaTarihi(),
                b.getGuncellenmeTarihi());
    }
}
