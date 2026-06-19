package com.artademi.finance;

import java.math.BigDecimal;
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
public interface AccrualRepository
        extends JpaRepository<Accrual, Long>, JpaSpecificationExecutor<Accrual> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT a FROM Accrual a WHERE a.id = :id")
    Optional<Accrual> findScopedById(@Param("id") Long id);

    /**
     * Bir ogrencinin TOPLAM tahakkugu. COALESCE ile bos sonuc 0 doner. JPQL oldugu icin tenant
     * filtresine tabidir (yalnizca aktif tenant). Bakiye hesabinda kullanilir.
     */
    @Query("SELECT COALESCE(SUM(a.tutar), 0) FROM Accrual a WHERE a.ogrenci.id = :ogrenciId")
    BigDecimal sumTutarByOgrenci(@Param("ogrenciId") Long ogrenciId);

    /**
     * Bir ogrencinin tum tahakkuklari (finans ozeti listesi). JPQL oldugu icin tenant filtresine
     * tabidir. En yeni once.
     */
    @Query("SELECT a FROM Accrual a WHERE a.ogrenci.id = :ogrenciId ORDER BY a.id DESC")
    List<Accrual> findByOgrenci(@Param("ogrenciId") Long ogrenciId);

    /**
     * Verilen ogrenci+grup+donem icin tahakkuk var mi? Otomatik uretimin IDEMPOTENT olmasi icin
     * (ayni donem tekrar calistirilinca mukerrer tahakkuk olusmamasi) kullanilir. JPQL oldugu icin
     * tenant filtresine tabidir (yalnizca aktif tenant kapsaminda kontrol).
     */
    @Query("SELECT (COUNT(a) > 0) FROM Accrual a WHERE a.ogrenci.id = :ogrenciId "
            + "AND a.grup.id = :grupId AND a.donem = :donem")
    boolean existsByOgrenciAndGrupAndDonem(@Param("ogrenciId") Long ogrenciId,
            @Param("grupId") Long grupId, @Param("donem") String donem);
}
