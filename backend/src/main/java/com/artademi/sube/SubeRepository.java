package com.artademi.sube;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi otomatik/her zaman acik oldugundan, buradaki SORGULAR yalnizca
 * aktif tenant'in kayitlariyla calisir; ek tenant_id kosulu yazilmamalidir.
 *
 * <p><b>ONEMLI:</b> Hibernate {@code @Filter} PK ile {@code findById} cagrilarina UYGULANMAZ.
 * Bu yuzden id ile guvenli erisim {@link #findScopedById} JPQL sorgusu uzerinden yapilir.
 */
public interface SubeRepository extends JpaRepository<Sube, Long>, JpaSpecificationExecutor<Sube> {

    /**
     * id ile tenant-guvenli erisim. JPQL oldugu icin global tenant filtresi uygulanir:
     * baska tenant'in subesi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT s FROM Sube s WHERE s.id = :id")
    Optional<Sube> findScopedById(@Param("id") Long id);
}
