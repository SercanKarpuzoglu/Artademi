package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Ogretmen yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 *
 * <p>{@code branslar}: ogretmene atanmis branslarin {@link BranchRef} ozetleri (branchLinks'ten
 * map'lenir). Branch, TeacherBranch.branch @ManyToOne uzerinden tenant-filtreli yuklenir.
 */
public record TeacherResponse(
        Long id,
        String ad,
        String soyad,
        String telefon,
        String email,
        String keycloakUserId,
        HakedisTipi hakedisTipi,
        BigDecimal saatlikUcret,
        BigDecimal ciroOrani,
        boolean aktif,
        List<BranchRef> branslar,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Brans ozeti (id + ad); tam Branch DTO'su yerine yeterli minimum bilgi. */
    public record BranchRef(Long id, String ad) {
    }

    public static TeacherResponse from(Teacher t) {
        List<BranchRef> branslar = t.getBranchLinks().stream()
                .map(link -> new BranchRef(link.getBranch().getId(), link.getBranch().getAd()))
                .sorted(Comparator.comparing(BranchRef::ad, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return new TeacherResponse(
                t.getId(),
                t.getAd(),
                t.getSoyad(),
                t.getTelefon(),
                t.getEmail(),
                t.getKeycloakUserId(),
                t.getHakedisTipi(),
                t.getSaatlikUcret(),
                t.getCiroOrani(),
                t.isAktif(),
                branslar,
                t.getOlusturulmaTarihi(),
                t.getGuncellenmeTarihi());
    }
}
