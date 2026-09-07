package com.artademi.gizli;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Global tenant filtresi otomatik/her zaman acik oldugundan buradaki sorgular yalnizca aktif
 * kurumun satirlariyla calisir; ek tenant_id kosulu YAZILMAZ.
 *
 * <p>PK ile {@code findById} KULLANILMAZ (filtre PK-find'a uygulanmaz); erisim anahtar
 * uzerinden turetilmis sorgularla yapilir.
 */
public interface GizliAyarRepository extends JpaRepository<GizliAyar, Long> {

    Optional<GizliAyar> findByAnahtar(String anahtar);

    List<GizliAyar> findAllByOrderByAnahtarAsc();

    void deleteByAnahtar(String anahtar);
}
