package com.artademi.telafi;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi her zaman acik; ek tenant_id kosulu YAZILMAZ.
 *
 * <p><b>ONEMLI:</b> {@code findById} KULLANILMAZ — filtre PK-find'a uygulanmaz.
 */
public interface TelafiHakkiRepository extends JpaRepository<TelafiHakki, Long> {

    @Query("SELECT t FROM TelafiHakki t WHERE t.id = :id")
    Optional<TelafiHakki> findScopedById(@Param("id") Long id);

    /** En yeni hak once; liste varsayilani. */
    @Query("SELECT t FROM TelafiHakki t JOIN FETCH t.ogrenci ORDER BY t.verilmeTarihi DESC, t.id DESC")
    List<TelafiHakki> tumu();

    @Query("SELECT t FROM TelafiHakki t JOIN FETCH t.ogrenci WHERE t.durum = :durum "
            + "ORDER BY t.verilmeTarihi DESC, t.id DESC")
    List<TelafiHakki> durumaGore(@Param("durum") TelafiDurumu durum);

    @Query("SELECT t FROM TelafiHakki t JOIN FETCH t.ogrenci WHERE t.ogrenci.id = :ogrenciId "
            + "ORDER BY t.verilmeTarihi DESC, t.id DESC")
    List<TelafiHakki> ogrenciyeGore(@Param("ogrenciId") Long ogrenciId);

    /** Panel rozeti: kullanilmayi bekleyen hak sayisi. */
    long countByDurum(TelafiDurumu durum);

    /**
     * Bu devamsizliktan ZATEN hak verilmis mi? Ayni devamsizliktan iki hak dogmamali.
     * (DB'de de kismi unique indeks var; bu kontrol anlasilir hata mesaji icindir.)
     */
    @Query("SELECT t FROM TelafiHakki t WHERE t.ogrenci.id = :ogrenciId "
            + "AND t.kaynakOturum.id = :oturumId")
    Optional<TelafiHakki> kaynaktanVerilmisMi(@Param("ogrenciId") Long ogrenciId,
            @Param("oturumId") Long oturumId);

    /** Hak verilmis (ogrenci, oturum) ciftleri — aday listesini filtrelemek icin, tek sorguda. */
    @Query("SELECT t.ogrenci.id, t.kaynakOturum.id FROM TelafiHakki t "
            + "WHERE t.kaynakOturum.id IS NOT NULL")
    List<Object[]> hakVerilmisCiftler();
}
