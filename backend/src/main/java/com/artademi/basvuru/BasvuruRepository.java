package com.artademi.basvuru;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Global tenant filtresi otomatik/her zaman acik oldugundan, buradaki SORGULAR yalnizca
 * aktif tenant'in kayitlariyla calisir; ek tenant_id kosulu yazilmamalidir.
 *
 * <p><b>ONEMLI:</b> Hibernate {@code @Filter} PK ile {@code findById} cagrilarina UYGULANMAZ.
 * id ile guvenli erisim {@link #findScopedById} uzerinden yapilir.
 */
public interface BasvuruRepository
        extends JpaRepository<Basvuru, Long>, JpaSpecificationExecutor<Basvuru> {

    /** id ile tenant-guvenli erisim (JPQL -> filtre uygulanir; yabanci kayit BULUNAMAZ). */
    @Query("SELECT b FROM Basvuru b WHERE b.id = :id")
    Optional<Basvuru> findScopedById(@Param("id") Long id);

    /** Liste: en yeni basvuru once. */
    Page<Basvuru> findAllByOrderByOlusturulmaTarihiDesc(Pageable pageable);

    /** Duruma gore liste: en yeni once. */
    Page<Basvuru> findByDurumOrderByOlusturulmaTarihiDesc(BasvuruDurumu durum, Pageable pageable);

    /** Panelde rozet icin: ilgilenilmemis basvuru sayisi. */
    long countByDurum(BasvuruDurumu durum);

    /**
     * Ayni telefonla yakin zamanda gelen basvurular — mukerrer gonderimi engellemek icin.
     * Formu iki kez gonderen veli iki kayit olusturmamali.
     */
    @Query("SELECT b FROM Basvuru b WHERE b.telefon = :telefon AND b.olusturulmaTarihi > :esik")
    List<Basvuru> findRecentByTelefon(@Param("telefon") String telefon,
            @Param("esik") Instant esik);
}
