package com.artademi.donem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonemRepository extends JpaRepository<Donem, Long> {

    @Query("SELECT d FROM Donem d WHERE d.id = :id")
    Optional<Donem> findScopedById(@Param("id") Long id);

    @Query("SELECT d FROM Donem d ORDER BY d.baslangic DESC")
    List<Donem> findAllSirali();

    @Query("SELECT d FROM Donem d WHERE d.aktif = :aktif ORDER BY d.baslangic DESC")
    List<Donem> findByAktif(@Param("aktif") boolean aktif);
}
