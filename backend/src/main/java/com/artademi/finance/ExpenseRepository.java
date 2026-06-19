package com.artademi.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public interface ExpenseRepository
        extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT e FROM Expense e WHERE e.id = :id")
    Optional<Expense> findScopedById(@Param("id") Long id);

    /**
     * Verilen [from,to] araligindaki TUM giderlerin toplami (RAPOR). COALESCE ile bos sonuc 0. JPQL
     * oldugu icin global tenant filtresine tabidir (yalnizca aktif tenant). Salt okunur.
     */
    @Query("SELECT COALESCE(SUM(e.tutar), 0) FROM Expense e WHERE e.giderTarihi BETWEEN :from AND :to")
    BigDecimal sumTutarByTarihAraligi(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
