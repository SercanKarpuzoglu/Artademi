package com.artademi.student;

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
public interface StudentRepository
        extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findScopedById(@Param("id") Long id);

    /**
     * Kardes eslestirme: ayni tenant icinde, kendisi haricinde, verilen anne VEYA baba
     * TC'sine sahip ogrenciler. Bos/null veli TC'leri eslesmemeli — bu yuzden parametreler
     * null oldugunda o kosul kapatilir.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE s.id <> :selfId
              AND (
                   (:anneTc IS NOT NULL AND s.anneTcKimlikNo = :anneTc)
                OR (:babaTc IS NOT NULL AND s.babaTcKimlikNo = :babaTc)
              )
            """)
    List<Student> findSiblings(
            @Param("selfId") Long selfId,
            @Param("anneTc") String anneTc,
            @Param("babaTc") String babaTc);
}
