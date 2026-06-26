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
 * <p>Model C: {@code hakedisler} ogretmenin TANIMLADIGI hakedis tipleri + oranlari (her tip icin
 * yalnizca eslesen tutar alani dolu). {@code branslar} atanmis branslarin {@link BranchRef}
 * ozetleri (branchLinks'ten map'lenir).
 */
public record TeacherResponse(
        Long id,
        String ad,
        String soyad,
        String telefon,
        String email,
        String keycloakUserId,
        List<HakedisRow> hakedisler,
        boolean aktif,
        List<BranchRef> branslar,
        Instant olusturulmaTarihi,
        Instant guncellenmeTarihi) {

    /** Brans ozeti (id + ad); tam Branch DTO'su yerine yeterli minimum bilgi. */
    public record BranchRef(Long id, String ad) {
    }

    /**
     * Tek hakedis satiri ozeti (Model C). Yalnizca {@code tip} ile eslesen tutar alani doludur,
     * digerleri null.
     */
    public record HakedisRow(
            HakedisTipi tip,
            BigDecimal saatlikUcret,
            BigDecimal ciroOrani,
            BigDecimal dersBasiUcret) {
    }

    public static TeacherResponse from(Teacher t) {
        List<BranchRef> branslar = t.getBranchLinks().stream()
                .map(link -> new BranchRef(link.getBranch().getId(), link.getBranch().getAd()))
                .sorted(Comparator.comparing(BranchRef::ad, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        List<HakedisRow> hakedisler = t.getHakedisler().stream()
                .map(h -> new HakedisRow(h.getTip(), h.getSaatlikUcret(), h.getCiroOrani(),
                        h.getDersBasiUcret()))
                .sorted(Comparator.comparing(h -> h.tip().name()))
                .toList();
        return new TeacherResponse(
                t.getId(),
                t.getAd(),
                t.getSoyad(),
                t.getTelefon(),
                t.getEmail(),
                t.getKeycloakUserId(),
                hakedisler,
                t.isAktif(),
                branslar,
                t.getOlusturulmaTarihi(),
                t.getGuncellenmeTarihi());
    }
}
