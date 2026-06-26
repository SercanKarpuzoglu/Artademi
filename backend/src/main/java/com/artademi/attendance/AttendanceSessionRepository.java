package com.artademi.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi otomatik/her zaman acik oldugundan, buradaki SORGULAR
 * (JPQL ve Criteria/Specification) yalnizca aktif tenant'in kayitlariyla calisir;
 * ek tenant_id kosulu yazmaya gerek yoktur ve yazilmamalidir (bkz. multi-tenancy).
 *
 * <p><b>ONEMLI:</b> Hibernate {@code @Filter} PK ile {@code findById}/{@code EntityManager.find}
 * cagrilarina UYGULANMAZ (yalnizca sorgulara uygulanir). Bu yuzden id ile guvenli erisim
 * {@link #findScopedById} JPQL sorgusu uzerinden yapilir; boylece tenant izolasyonu korunur.
 * Filtreli/dinamik liste icin {@link JpaSpecificationExecutor} kullanilir.
 */
public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, Long>, JpaSpecificationExecutor<AttendanceSession> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT s FROM AttendanceSession s WHERE s.id = :id")
    Optional<AttendanceSession> findScopedById(@Param("id") Long id);

    /**
     * Verilen grup+tarih icin oturum var mi? JPQL oldugu icin tenant filtresine tabidir (yalnizca
     * aktif tenant kapsaminda). Mukerrer oturum engellemesi icin (DB unique kisit ile de zorlanir).
     */
    @Query("SELECT (COUNT(s) > 0) FROM AttendanceSession s "
            + "WHERE s.grup.id = :grupId AND s.tarih = :tarih")
    boolean existsByGrupAndTarih(@Param("grupId") Long grupId, @Param("tarih") LocalDate tarih);

    /**
     * Bir ogretmenin gruplarindaki [from,to] araligindaki yoklama OTURUMU sayisi (her oturum = 1 ders
     * birimi). SAATLIK hakediş hesabinda kullanilir. JPQL oldugu icin tenant filtresine tabidir
     * (yalnizca aktif tenant). {@code s.grup.ogretmen.id} yolu grup ve ogretmen non-null gerektirir.
     */
    @Query("SELECT COUNT(s) FROM AttendanceSession s "
            + "WHERE s.grup.ogretmen.id = :ogretmenId AND s.tarih BETWEEN :from AND :to")
    long countByOgretmenAndTarihAraligi(@Param("ogretmenId") Long ogretmenId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * BELIRLI bir grubun [from,to] araligindaki yoklama OTURUMU sayisi (her oturum = 1 ders birimi).
     * Model C: hakedis tipi gruba bagli oldugundan SAATLIK/OZEL_DERS hesabi grup bazinda yapilir.
     * JPQL oldugu icin tenant filtresine tabidir (yalnizca aktif tenant).
     */
    @Query("SELECT COUNT(s) FROM AttendanceSession s "
            + "WHERE s.grup.id = :grupId AND s.tarih BETWEEN :from AND :to")
    long countByGrupAndTarihAraligi(@Param("grupId") Long grupId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    // NOT: grup + [from,to] tarih araligi sorgusu, opsiyonel sinirlarda Postgres untyped-null
    // hatasini onlemek icin Specification ile yapilir (bkz. AttendanceSessionSpecifications +
    // AttendanceService.listByGroup), JPQL "IS NULL OR" anti-pattern'i ile DEGIL.
}
