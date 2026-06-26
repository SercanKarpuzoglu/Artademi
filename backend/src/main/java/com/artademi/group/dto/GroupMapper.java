package com.artademi.group.dto;

import com.artademi.branch.Branch;
import com.artademi.group.Group;
import com.artademi.group.GrupTipi;
import com.artademi.room.Room;
import com.artademi.teacher.HakedisTipi;
import com.artademi.teacher.Teacher;

/**
 * Request DTO'larini Group entity'sine yansitir. tenant_id ve aktif BURADA ELLE yonetilmez:
 * tenant @PrePersist'te TenantContext'ten gelir, aktif ise serviste (yeni kayitta true,
 * degisiklikte PATCH endpoint'i) yonetilir.
 *
 * <p>brans/ogretmen/salon, tenant-guvenli cozulmus ({@code findScopedById}) entity'lerle baglanir;
 * cozumleme servis katmaninda yapilir, mapper yalnizca set eder. salon OZEL grupta null gelebilir.
 *
 * <p>Tipe gore yok sayilan ucret alani temizlenir: GRUP'ta dersBasiUcret null, OZEL'de aylikAidat
 * null set edilir (validasyon zaten ilgili alanin dolu/gecerli olmasini garanti eder).
 */
public final class GroupMapper {

    private GroupMapper() {
    }

    /** Yeni grup olusturur; aktif true ile baslar (entity varsayilani). */
    public static Group toNewEntity(CreateGroupRequest req, Branch brans, Teacher ogretmen, Room salon) {
        Group g = Group.create();
        apply(g, req.ad(), req.tip(), req.hakedisTipi(), brans, ogretmen, salon, req.seviye(),
                req.aylikAidat(), req.dersBasiUcret());
        g.setAktif(true);
        return g;
    }

    /** Mevcut grubun alanlarini gunceller; aktif'e DOKUNMAZ. */
    public static void applyUpdate(Group g, UpdateGroupRequest req, Branch brans, Teacher ogretmen, Room salon) {
        apply(g, req.ad(), req.tip(), req.hakedisTipi(), brans, ogretmen, salon, req.seviye(),
                req.aylikAidat(), req.dersBasiUcret());
    }

    /**
     * Model C: grup tipinden varsayilan hakedis tipi. GRUP->SAATLIK, OZEL->OZEL_DERS. Istekte
     * hakedisTipi verilmediyse bu uygulanir; verildiyse istemcininki korunur.
     */
    private static HakedisTipi defaultHakedisTipi(GrupTipi tip) {
        return tip == GrupTipi.OZEL ? HakedisTipi.OZEL_DERS : HakedisTipi.SAATLIK;
    }

    private static void apply(Group g, String ad, GrupTipi tip, HakedisTipi hakedisTipi, Branch brans,
            Teacher ogretmen, Room salon, String seviye, java.math.BigDecimal aylikAidat,
            java.math.BigDecimal dersBasiUcret) {
        g.setAd(ad);
        g.setTip(tip);
        g.setHakedisTipi(hakedisTipi != null ? hakedisTipi : defaultHakedisTipi(tip));
        g.setBrans(brans);
        g.setOgretmen(ogretmen);
        g.setSeviye(seviye);
        switch (tip) {
            case GRUP -> {
                g.setSalon(salon);
                g.setAylikAidat(aylikAidat);
                g.setDersBasiUcret(null);
            }
            case OZEL -> {
                g.setSalon(salon);
                g.setAylikAidat(null);
                g.setDersBasiUcret(dersBasiUcret);
            }
        }
    }
}
