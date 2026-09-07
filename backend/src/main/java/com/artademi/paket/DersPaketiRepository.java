package com.artademi.paket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Global tenant filtresi her zaman acik. {@code findById} KULLANILMAZ. */
public interface DersPaketiRepository extends JpaRepository<DersPaketi, Long> {

    @Query("SELECT p FROM DersPaketi p WHERE p.id = :id")
    Optional<DersPaketi> findScopedById(@Param("id") Long id);

    @Query("SELECT p FROM DersPaketi p JOIN FETCH p.ogrenci LEFT JOIN FETCH p.grup "
            + "ORDER BY p.satisTarihi DESC, p.id DESC")
    List<DersPaketi> tumu();

    @Query("SELECT p FROM DersPaketi p JOIN FETCH p.ogrenci LEFT JOIN FETCH p.grup "
            + "WHERE p.ogrenci.id = :ogrenciId ORDER BY p.satisTarihi DESC, p.id DESC")
    List<DersPaketi> ogrenciyeGore(@Param("ogrenciId") Long ogrenciId);

    /**
     * Ogrencinin AKTIF paketleri, en eski once.
     *
     * <p>FIFO bilincli: once satin alinan once tuketilir. Aksi halde suresi yaklasan paket
     * bosta kalirken yeni paket harcanir ve ogrenci hak kaybeder.
     */
    @Query("SELECT p FROM DersPaketi p LEFT JOIN FETCH p.grup WHERE p.ogrenci.id = :ogrenciId "
            + "AND p.durum = com.artademi.paket.PaketDurumu.AKTIF "
            + "ORDER BY p.satisTarihi ASC, p.id ASC")
    List<DersPaketi> aktifPaketler(@Param("ogrenciId") Long ogrenciId);
}
