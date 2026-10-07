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
     * Kasaya islenmis tahsilat toplami. Kasa bakiyesi bu deger uzerinden HESAPLANIR;
     * bakiye hicbir yerde saklanmaz (saklanan bakiye zamanla sapar).
     *
     * <p>Kayit yoksa {@code null} doner — cagiran taraf sifira cevirir.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT SUM(p.tutar) FROM Payment p WHERE p.kasa.id = :kasaId")
    java.math.BigDecimal kasayaGirenToplam(@Param("kasaId") Long kasaId);

    /**
     * Bir tahsilata bugune kadar yapilmis iadelerin toplami — POZITIF doner (iade satirlari
     * negatiftir, {@code -SUM} ile cevrilir). Kismi iade siniri bu deger uzerinden kontrol edilir.
     *
     * <p>JPQL oldugu icin yumusak silme filtresine de tabidir: silinen iade satiri sayiya girmez,
     * yani iade "geri alinmis" olur ve tutar yeniden iade edilebilir.
     */
    @Query("SELECT COALESCE(-SUM(p.tutar), 0) FROM Payment p WHERE p.iadeEdilenOdeme.id = :odemeId")
    BigDecimal iadeToplami(@Param("odemeId") Long odemeId);

    /** Bu tahsilatin (silinmemis) iadesi var mi? Iadesi olan tahsilat SILINEMEZ. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.iadeEdilenOdeme.id = :odemeId")
    long countIadeByOdeme(@Param("odemeId") Long odemeId);

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
     * sonuc 0 doner. JPQL oldugu icin tenant filtresine tabidir (yalnizca aktif tenant).
     * {@code p.grup.ogretmen.id} yolu grup non-null gerektirir, yani grubu olmayan (grup_id NULL)
     * tahsilatlar bu toplama OTOMATIK dahil edilmez.
     *
     * <p>⚠️ <b>SU AN CAGRILMIYOR.</b> Model C'de hakedis GRUP bazinda hesaplanir
     * ({@link #sumTutarByGrupAndTarihAraligi}); bu ogretmen-bazli esdegeri duruyor ama kullanilmiyor.
     * Yine de ayni iade kurali (iade = orijinal odemenin ayi) uygulandi: iki ciro sorgusunun
     * birbirinden sapmasi, ileride bunu kullanan kisi icin sessiz bir tuzak olurdu.
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p LEFT JOIN p.iadeEdilenOdeme o "
            + "WHERE p.grup.ogretmen.id = :ogretmenId "
            + "AND COALESCE(o.odemeTarihi, p.odemeTarihi) BETWEEN :from AND :to")
    BigDecimal sumTutarByOgretmenAndTarihAraligi(@Param("ogretmenId") Long ogretmenId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * BELIRLI bir gruba ait [from,to] araligindaki tahsilatlarin TOPLAMI — <b>CIRO_ORANI hakedisi
     * icin</b>. COALESCE ile bos sonuc 0. Model C: hakedis hesabi grup bazinda yapilir.
     * JPQL oldugu icin tenant filtresine tabidir (yalnizca aktif tenant).
     *
     * <p><b>⚠️ IADE, ORIJINAL ODEMENIN AYINA yazilir</b> (urun karari 2026-10-07): bir satirin ciro
     * ayi, iade satirlari icin iade edilen ODEMENIN tarihidir, kendi tarihi degil. Boylece Eylul'de
     * alinan paranin Ekim'de iadesi <b>Eylul</b> cirosunu duzeltir; eskiden Ekim'e yaziliyordu ve o
     * grupta Ekim'de baska tahsilat yoksa hakedis <b>eksiye</b> dusuyordu.
     *
     * <p>⚠️ Bu, Gelirler ozeti ve kasa bakiyesinden BILINCLI olarak farklidir: orada iade, parayi
     * fiilen verdigimiz ayda gorunur (nakit esasi). Burada ise komisyonun hesaplandigi cironun
     * duzeltilmesi gerekir, o yuzden tahakkuk esasi.
     *
     * <p>⚠️ {@code LEFT JOIN} sart: {@code p.iadeEdilenOdeme.odemeTarihi} seklinde ortuk yol
     * yazilirsa Hibernate INNER JOIN uretir ve <b>iade OLMAYAN tum satirlar toplamdan duser</b>.
     */
    @Query("SELECT COALESCE(SUM(p.tutar), 0) FROM Payment p LEFT JOIN p.iadeEdilenOdeme o "
            + "WHERE p.grup.id = :grupId "
            + "AND COALESCE(o.odemeTarihi, p.odemeTarihi) BETWEEN :from AND :to")
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

    /** Verilen ogrenciler icin odeme toplamlari (ogrenci listesi bakiye sutunu). */
    @Query("SELECT p.ogrenci.id, COALESCE(SUM(p.tutar), 0) FROM Payment p "
            + "WHERE p.ogrenci.id IN :ids GROUP BY p.ogrenci.id")
    List<Object[]> sumTutarGroupByOgrenciIn(@Param("ids") java.util.Collection<Long> ids);

    // --- Yumusak silme on-kontrolleri (SilmeService): silinmemis bagli kayit sayilari ---
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.accrual.id = :id")
    long countByAccrual(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.kasa.id = :id")
    long countByKasa(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.ogrenci.id = :id")
    long countByOgrenci(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.grup.id = :id")
    long countByGrup(@Param("id") Long id);

}
