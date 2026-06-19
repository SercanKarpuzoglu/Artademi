package com.artademi.schedule;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;

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
public interface ScheduleRepository
        extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

    /**
     * id ile tenant-guvenli erisim. JPQL sorgusu oldugu icin global tenant filtresi
     * uygulanir: baska tenant'in kaydi bu cagriyla BULUNAMAZ (-> 404).
     */
    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findScopedById(@Param("id") Long id);

    /**
     * Cakisma kontrolu icin: verilen gun'de, aktif ve verilen saat araligi ile ortusen
     * programlar. Ortusme kosulu: {@code baslangicSaati < :bitis AND bitisSaati > :baslangic}.
     * JPQL oldugu icin yalnizca aktif tenant'in kayitlarini doner (tenant filtresi). Salon/ogretmen
     * cakismasi servis katmaninda grubun salon/ogretmen id'leri karsilastirilarak belirlenir.
     */
    @Query("SELECT s FROM Schedule s WHERE s.gun = :gun AND s.aktif = true "
            + "AND s.baslangicSaati < :bitis AND s.bitisSaati > :baslangic")
    List<Schedule> findOverlappingActive(@Param("gun") HaftaGunu gun,
            @Param("baslangic") LocalTime baslangic, @Param("bitis") LocalTime bitis);

    /**
     * Bir grubun tum programlari. JPQL oldugu icin tenant filtresi uygulanir; grup_id'nin baska
     * tenant'a ait olamamasi servis katmaninda (group findScopedById) garanti edilir.
     *
     * <p>Siralama SQL'de YAPILMAZ: {@code gun} @Enumerated(STRING) oldugundan "ORDER BY gun"
     * alfabetik (yanlis) siralar. Hafta sirasi servis katmaninda enum ordinal'ine gore uygulanir.
     */
    @Query("SELECT s FROM Schedule s WHERE s.grup.id = :grupId")
    List<Schedule> findByGrupId(@Param("grupId") Long grupId);
}
