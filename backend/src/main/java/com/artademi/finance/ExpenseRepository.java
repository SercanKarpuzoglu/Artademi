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
     * Kasadan cikan gider toplami. Kasa bakiyesi bu deger uzerinden HESAPLANIR;
     * bakiye hicbir yerde saklanmaz. Kayit yoksa {@code null} doner.
     */
    @Query("SELECT SUM(e.tutar) FROM Expense e WHERE e.kasa.id = :kasaId")
    java.math.BigDecimal kasadanCikanToplam(@Param("kasaId") Long kasaId);

    /** Tedarikciye yapilan gider toplami ("bu ay kime ne kadar odedik"). */
    @Query("SELECT SUM(e.tutar) FROM Expense e WHERE e.tedarikci.id = :tedarikciId")
    java.math.BigDecimal tedarikciyeOdenenToplam(@Param("tedarikciId") Long tedarikciId);

    /**
     * Verilen [from,to] araligindaki TUM giderlerin toplami (RAPOR). COALESCE ile bos sonuc 0. JPQL
     * oldugu icin global tenant filtresine tabidir (yalnizca aktif tenant). Salt okunur.
     */
    @Query("SELECT COALESCE(SUM(e.tutar), 0) FROM Expense e WHERE e.giderTarihi BETWEEN :from AND :to")
    BigDecimal sumTutarByTarihAraligi(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // --- Yumusak silme on-kontrolleri (SilmeService): silinmemis bagli kayit sayilari ---
    @Query("SELECT COUNT(x) FROM Expense x WHERE x.kasa.id = :id")
    long countByKasa(@Param("id") Long id);

    @Query("SELECT COUNT(x) FROM Expense x WHERE x.tedarikci.id = :id")
    long countByTedarikci(@Param("id") Long id);

}
