package com.artademi.tedarikci;

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
public interface TedarikciRepository extends JpaRepository<Tedarikci, Long> {

    @Query("SELECT t FROM Tedarikci t WHERE t.id = :id")
    Optional<Tedarikci> findScopedById(@Param("id") Long id);

    List<Tedarikci> findAllByOrderByAdAsc();

    List<Tedarikci> findByAktifTrueOrderByAdAsc();

    Optional<Tedarikci> findByAd(String ad);
}
