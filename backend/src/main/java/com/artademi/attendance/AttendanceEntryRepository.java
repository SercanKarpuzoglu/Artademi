package com.artademi.attendance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi otomatik/her zaman acik oldugundan, buradaki SORGULAR (JPQL) yalnizca aktif
 * tenant'in kayitlariyla calisir; ek tenant_id kosulu yazmaya gerek yoktur ve yazilmamalidir
 * (bkz. multi-tenancy).
 *
 * <p><b>ONEMLI:</b> id ile guvenli erisim PK-find yerine JPQL ({@code findScopedById}) ile yapilir;
 * boylece tenant izolasyonu korunur. Bu modulde girisler her zaman bir oturum baglaminda
 * yuklenir/guncellenir.
 */
public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, Long> {

    /**
     * Bir oturumun tum girisleri. JPQL oldugu icin tenant filtresine tabidir; session_id'nin baska
     * tenant'a ait olamamasi servis katmaninda (session findScopedById) garanti edilir.
     */
    @Query("SELECT e FROM AttendanceEntry e WHERE e.session.id = :sessionId")
    List<AttendanceEntry> findBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Bir oturumdaki belirli ogrencinin girisi (toplu guncellemede her ogrenci icin aranir). JPQL
     * oldugu icin tenant filtresine tabidir.
     */
    @Query("SELECT e FROM AttendanceEntry e WHERE e.session.id = :sessionId AND e.ogrenci.id = :ogrenciId")
    Optional<AttendanceEntry> findBySessionIdAndOgrenciId(
            @Param("sessionId") Long sessionId,
            @Param("ogrenciId") Long ogrenciId);

    /**
     * Devamsizlik raporu ham verisi: tarih araligindaki her ogrenci icin durum bazinda sayim.
     *
     * <p>Tenant filtresi JPQL'e OTOMATIK uygulanir (AttendanceEntry ve Student TenantAware) —
     * bu yuzden sorguda tenant kosulu YAZILMAZ ve baska kurumun verisi gelemez.
     *
     * @return satirlar: [ogrenciId, ad, soyad, durum, adet]
     */
    @Query("""
            SELECT e.ogrenci.id, e.ogrenci.ad, e.ogrenci.soyad, e.durum, COUNT(e)
            FROM AttendanceEntry e
            WHERE e.session.tarih BETWEEN :baslangic AND :bitis
              AND (:grupId IS NULL OR e.session.grup.id = :grupId)
            GROUP BY e.ogrenci.id, e.ogrenci.ad, e.ogrenci.soyad, e.durum
            """)
    List<Object[]> katilimSayimlari(@Param("baslangic") java.time.LocalDate baslangic,
            @Param("bitis") java.time.LocalDate bitis,
            @Param("grupId") Long grupId);

    /** Tarih araligindaki oturum sayisi (rapor basligindaki "toplam ders"). */
    @Query("""
            SELECT COUNT(s) FROM AttendanceSession s
            WHERE s.tarih BETWEEN :baslangic AND :bitis
              AND (:grupId IS NULL OR s.grup.id = :grupId)
            """)
    long oturumSayisi(@Param("baslangic") java.time.LocalDate baslangic,
            @Param("bitis") java.time.LocalDate bitis,
            @Param("grupId") Long grupId);
}
