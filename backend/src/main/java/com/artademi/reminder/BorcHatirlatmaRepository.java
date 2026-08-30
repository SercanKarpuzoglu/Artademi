package com.artademi.reminder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Borc hatirlatma izi (tenant filtresi otomatik). */
public interface BorcHatirlatmaRepository extends JpaRepository<BorcHatirlatma, UUID> {

    /** Verilen tarihten SONRA hatirlatma gonderilmis ogrenci id'leri (mukerrer gonderim kalkani). */
    @Query("SELECT b.ogrenciId FROM BorcHatirlatma b WHERE b.createdAt >= :sinir")
    List<Long> sonGonderilenOgrenciIdleri(@Param("sinir") Instant sinir);
}
