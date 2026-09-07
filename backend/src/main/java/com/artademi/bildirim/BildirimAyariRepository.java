package com.artademi.bildirim;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Kurum basina TEK ayar satiri. Global tenant filtresi acik oldugundan {@code findAll()}
 * yalnizca aktif tenant'in satirini dondurur; ek tenant_id kosulu yazilmaz.
 */
public interface BildirimAyariRepository extends JpaRepository<BildirimAyari, Long> {

    /**
     * Aktif tenant'in ayari. Turetilmis sorgu DEGIL, {@code findAll()} uzerinden ilk kayit:
     * tenant filtresi zaten tek satira indirger ve ayrica alan bazli kosul gerekmez.
     */
    default Optional<BildirimAyari> aktifTenantAyari() {
        return findAll().stream().findFirst();
    }
}
