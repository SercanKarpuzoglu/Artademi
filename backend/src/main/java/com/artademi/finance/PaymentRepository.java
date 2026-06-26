package com.artademi.finance;

import java.math.BigDecimal;
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
public interface PaymentRepository
        extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findScopedById(@Param("id") Long id);

    /**
     * Bir ogrencinin TOPLAM tahsilati. COALESCE ile bos sonuc 0 doner. JPQL oldugu icin tenant
     * filtresine tabidir (yalnizca aktif tenant). Bakiye hesabinda kullanilir.
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p WHERE p.ogrenci.id = :ogrenciId")
    BigDecimal sumTutarByOgrenci(@Param("ogrenciId") Long ogrenciId);

    /**
     * Bir ogrencinin tum tahsilatlari (finans ozeti listesi). JPQL oldugu icin tenant filtresine
     * tabidir. En yeni once.
     */
    @Query("SELECT p FROM Payment p WHERE p.ogrenci.id = :ogrenciId ORDER BY p.id DESC")
    List<Payment> findByOgrenci(@Param("ogrenciId") Long ogrenciId);

    /**
     * Bir ogretmenin gruplarina ait [from,to] araligindaki tahsilatlarin TOPLAMI. COALESCE ile bos
     * sonuc 0 doner. JPQL oldugu icin tenant filtresine tabidir (yalnizca aktif tenant). CIRO_ORANI
     * hakediş hesabinda kullanilir. {@code p.grup.ogretmen.id} yolu grup non-null gerektirir, yani
     * grubu olmayan (grup_id NULL) tahsilatlar bu toplama OTOMATIK dahil edilmez.
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p "
            + "WHERE p.grup.ogretmen.id = :ogretmenId AND p.odemeTarihi BETWEEN :from AND :to")
    BigDecimal sumTutarByOgretmenAndTarihAraligi(@Param("ogretmenId") Long ogretmenId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * BELIRLI bir gruba ait [from,to] araligindaki tahsilatlarin TOPLAMI. COALESCE ile bos sonuc 0.
     * Model C: CIRO_ORANI hakedis hesabi grup bazinda yapilir (her grup kendi hakedis tipiyle).
     * JPQL oldugu icin tenant filtresine tabidir (yalnizca aktif tenant).
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p "
            + "WHERE p.grup.id = :grupId AND p.odemeTarihi BETWEEN :from AND :to")
    BigDecimal sumTutarByGrupAndTarihAraligi(@Param("grupId") Long grupId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Verilen [from,to] araligindaki TUM tahsilatlarin toplami (RAPOR). COALESCE ile bos sonuc 0.
     * JPQL oldugu icin global tenant filtresine tabidir (yalnizca aktif tenant). Salt okunur.
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p WHERE p.odemeTarihi BETWEEN :from AND :to")
    BigDecimal sumTutarByTarihAraligi(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Ogrenci bazinda toplam tahsilat (RAPOR — ogrenci bakiyeleri). [ogrenciId, SUM] satirlari doner;
     * N+1 yerine tek sorgu. JPQL oldugu icin tenant filtresine tabidir. Salt okunur.
     */
    @Query("SELECT p.ogrenci.id, COALESCE(SUM(p.tutar), 0) FROM Payment p GROUP BY p.ogrenci.id")
    List<Object[]> sumTutarGroupByOgrenci();
}
