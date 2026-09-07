package com.artademi.kasa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi her zaman acik; ek tenant_id kosulu YAZILMAZ.
 *
 * <p><b>ONEMLI:</b> {@code findById} KULLANILMAZ — Hibernate filtresi PK-find'a uygulanmaz.
 */
public interface KasaRepository extends JpaRepository<Kasa, Long> {

    @Query("SELECT k FROM Kasa k WHERE k.id = :id")
    Optional<Kasa> findScopedById(@Param("id") Long id);

    List<Kasa> findAllByOrderByAdAsc();

    List<Kasa> findByAktifTrueOrderByAdAsc();

    Optional<Kasa> findByAd(String ad);
}
