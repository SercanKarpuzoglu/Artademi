package com.artademi.kasa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Kasa hareketleri (transfer + duzeltme). Tenant filtresi otomatik. */
public interface KasaHareketiRepository extends JpaRepository<KasaHareketi, Long> {

    @Query("SELECT h FROM KasaHareketi h WHERE h.id = :id")
    Optional<KasaHareketi> findScopedById(@Param("id") Long id);

    List<KasaHareketi> findByKasaIdOrderByTarihDescIdDesc(Long kasaId);

    /** Transferin IKI bacagi; silme grup uzerinden yapilir ki yarim transfer kalmasin. */
    List<KasaHareketi> findByTransferGrubu(UUID transferGrubu);

    /**
     * Kasanin elle girilen hareketlerinden net etki (girisler - cikislar).
     *
     * <p>Bos kasada {@code null} doner — cagiran taraf sifira cevirir. COALESCE'i JPQL'de
     * yazmak yerine Java'da ele almak, tip donusum surprizlerini onler.
     */
    @Query("SELECT SUM(CASE WHEN h.yon = com.artademi.kasa.HareketYonu.GIRIS "
            + "THEN h.tutar ELSE -h.tutar END) "
            + "FROM KasaHareketi h WHERE h.kasa.id = :kasaId")
    BigDecimal netHareket(@Param("kasaId") Long kasaId);
}
