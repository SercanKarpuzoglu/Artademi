package com.artademi.paket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Kontor tuketim satirlari. Kalan ders bu satirlarin sayisindan HESAPLANIR. */
public interface PaketKullanimRepository extends JpaRepository<PaketKullanim, Long> {

    long countByPaketId(Long paketId);

    /**
     * Ogrenci bu oturumdan kontor kullandi mi? Anahtar (ogrenci, oturum) — paket DEGIL:
     * iki paketi olan ogrenci ayni dersten iki kontor harcamamali.
     */
    Optional<PaketKullanim> findByOgrenciIdAndOturumId(Long ogrenciId, Long oturumId);

    /** Birden cok paketin tuketim sayisi tek sorguda (liste ekraninda N+1 olmasin). */
    @Query("SELECT k.paketId, COUNT(k) FROM PaketKullanim k WHERE k.paketId IN :paketIdler "
            + "GROUP BY k.paketId")
    List<Object[]> paketBasinaKullanim(@Param("paketIdler") List<Long> paketIdler);

    // --- Yumusak silme on-kontrolleri (SilmeService): silinmemis bagli kayit sayilari ---
    @Query("SELECT COUNT(k) FROM PaketKullanim k WHERE k.paketId = :id")
    long countByPaket(@Param("id") Long id);

    @Query("SELECT COUNT(k) FROM PaketKullanim k WHERE k.oturumId = :id")
    long countByOturum(@Param("id") Long id);

}
