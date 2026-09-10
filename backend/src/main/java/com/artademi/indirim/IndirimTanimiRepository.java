package com.artademi.indirim;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPQL -> tenant + silinmemis filtreleri otomatik. */
public interface IndirimTanimiRepository extends JpaRepository<IndirimTanimi, Long> {

    @Query("SELECT i FROM IndirimTanimi i WHERE i.id = :id")
    Optional<IndirimTanimi> findScopedById(@Param("id") Long id);

    @Query("SELECT i FROM IndirimTanimi i ORDER BY i.ad ASC")
    List<IndirimTanimi> findAllSirali();

    @Query("SELECT i FROM IndirimTanimi i WHERE i.aktif = :aktif ORDER BY i.ad ASC")
    List<IndirimTanimi> findByAktif(@Param("aktif") boolean aktif);
}
