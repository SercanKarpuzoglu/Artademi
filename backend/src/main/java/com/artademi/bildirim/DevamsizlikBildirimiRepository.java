package com.artademi.bildirim;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Devamsizlik bildirimi izleri (tenant filtresi otomatik). */
public interface DevamsizlikBildirimiRepository extends JpaRepository<DevamsizlikBildirimi, Long> {

    /**
     * Verilen oturumlar icin BILDIRIMI ZATEN GONDERILMIS (ogrenciId, oturumId) ciftleri.
     * Mukerrer gonderim kalkani; tek sorguda toplanir ki N+1 olmasin.
     */
    @Query("SELECT d.ogrenciId, d.oturumId FROM DevamsizlikBildirimi d "
            + "WHERE d.oturumId IN :oturumIdler")
    List<Object[]> gonderilmisCiftler(@Param("oturumIdler") List<Long> oturumIdler);
}
