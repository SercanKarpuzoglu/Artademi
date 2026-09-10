package com.artademi.bildirim;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPQL -> tenant filtreli. Rol/kullanici suzme serviste (roller virgullu metin). */
public interface UygulamaBildirimiRepository extends JpaRepository<UygulamaBildirimi, Long> {

    @Query("SELECT b FROM UygulamaBildirimi b WHERE b.id = :id")
    Optional<UygulamaBildirimi> findScopedById(@Param("id") Long id);

    /** En yeni en ustte; sayfa boyutu cagirandan (zil icin ~100 sonra rol suzmesi). */
    @Query("SELECT b FROM UygulamaBildirimi b ORDER BY b.id DESC")
    List<UygulamaBildirimi> sonBildirimler(Pageable pageable);
}
