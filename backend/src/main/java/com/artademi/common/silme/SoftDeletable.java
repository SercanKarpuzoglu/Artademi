package com.artademi.common.silme;

import java.time.Instant;

/**
 * Yumusak silinebilir entity sozlesmesi (V32). Uygulayan sinif {@code @Filter(name = FILTRE)} tasir; filtre
 * tanimi ({@code @FilterDef}, autoEnabled) Student uzerindedir ve tenant filtresi gibi her oturumda acilir.
 *
 * <p>NEDEN @SQLRestriction DEGIL: Hibernate 6.5'te @SQLRestriction bire-bir (ManyToOne) yuklemelere de
 * uygulanir — silinmis ogrencinin odemesi listelenirken FetchNotFound ile 500 verdi. @Filter ise yalnizca
 * sorgulara (JPQL/Criteria) uygulanir, PK ile yuklemeye (find / to-one proxy) UYGULANMAZ; tam istenen bu:
 * silinen kayit listelerden/aramalardan kalkar, ama odeme satiri hala ogrencinin adini gosterir (para izi).
 * Gercek DELETE yoktur; geri alma native UPDATE ile yapilir ({@code SilmeService.geriAl}).
 */
public interface SoftDeletable {

    /** Otomatik etkin filtre adi: {@code silindi_tarihi IS NULL}. */
    String FILTRE = "silinmemisFiltresi";

    Instant getSilindiTarihi();

    void setSilindiTarihi(Instant silindiTarihi);

    void setSilen(String silen);

    /** Damgala: tarih + kim. Cagiran, kaydi kaydeder (managed entity ise flush yeter). */
    default void sil(String kullanici) {
        setSilindiTarihi(Instant.now());
        setSilen(kullanici);
    }
}
