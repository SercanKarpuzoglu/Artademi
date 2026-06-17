package com.artademi.enrollment;

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
public interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT e FROM Enrollment e WHERE e.id = :id")
    Optional<Enrollment> findScopedById(@Param("id") Long id);

    /**
     * Verilen ogrenci+grup icin AKTIF kayit var mi? JPQL sorgusu oldugu icin tenant filtresine
     * tabidir (yalnizca aktif tenant kapsaminda kontrol). Mukerrer aktif kayit engellemesi icin
     * (DB tarafinda partial unique index ile de zorlanir).
     */
    @Query("""
            SELECT (COUNT(e) > 0) FROM Enrollment e
            WHERE e.ogrenci.id = :ogrenciId
              AND e.grup.id = :grupId
              AND e.durum = com.artademi.enrollment.EnrollmentDurumu.AKTIF
            """)
    boolean existsAktifByOgrenciAndGrup(
            @Param("ogrenciId") Long ogrenciId,
            @Param("grupId") Long grupId);
}
