package com.artademi.payout;

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
public interface PayoutRepository
        extends JpaRepository<Payout, Long>, JpaSpecificationExecutor<Payout> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT p FROM Payout p WHERE p.id = :id")
    Optional<Payout> findScopedById(@Param("id") Long id);

    /**
     * Model C: verilen ogretmen + donem + TIP icin hakediş zaten var mi? Mukerrer engeli artik TIP
     * bazinda (bir ogretmen ayni donemde birden cok tipte hakedis alabilir). JPQL oldugu icin tenant
     * filtresine tabidir (yalnizca aktif tenant kapsaminda; DB unique kisit ile de zorlanir).
     */
    @Query("SELECT (COUNT(p) > 0) FROM Payout p "
            + "WHERE p.ogretmen.id = :ogretmenId AND p.donem = :donem AND p.hakedisTipi = :tip")
    boolean existsByOgretmenAndDonemAndTip(@Param("ogretmenId") Long ogretmenId,
            @Param("donem") String donem, @Param("tip") com.artademi.teacher.HakedisTipi tip);

    /**
     * Verilen donemdeki hakedislerin toplami (RAPOR — finansal ozet/ogretmen hakedisleri). COALESCE
     * ile bos sonuc 0. JPQL oldugu icin global tenant filtresine tabidir. Salt okunur.
     */
    @Query("SELECT COALESCE(SUM(p.hesaplananTutar), 0) FROM Payout p WHERE p.donem = :donem")
    BigDecimal sumHesaplananByDonem(@Param("donem") String donem);

    /**
     * Verilen donemdeki hakedis kalemleri (RAPOR — ogretmen hakedisleri dokumu). JPQL oldugu icin
     * global tenant filtresine tabidir. Salt okunur. Sabit siralama icin id artan.
     */
    @Query("SELECT p FROM Payout p WHERE p.donem = :donem ORDER BY p.id ASC")
    List<Payout> findByDonem(@Param("donem") String donem);
}
