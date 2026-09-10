package com.artademi.indirim;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OgrenciIndirimiRepository extends JpaRepository<OgrenciIndirimi, Long> {

    @Query("SELECT o FROM OgrenciIndirimi o WHERE o.id = :id")
    Optional<OgrenciIndirimi> findScopedById(@Param("id") Long id);

    /** Ogrencinin tum atamalari (tanim FETCH; ekranda ve hesaplamada kullanilir). */
    @Query("SELECT o FROM OgrenciIndirimi o JOIN FETCH o.indirim WHERE o.ogrenci.id = :ogrenciId ORDER BY o.id DESC")
    List<OgrenciIndirimi> findByOgrenci(@Param("ogrenciId") Long ogrenciId);

    /** Bir tanimin AKTIF atama sayisi (silme engeli). */
    @Query("SELECT COUNT(o) FROM OgrenciIndirimi o WHERE o.indirim.id = :indirimId AND o.aktif = true")
    long countAktifByIndirim(@Param("indirimId") Long indirimId);
}
