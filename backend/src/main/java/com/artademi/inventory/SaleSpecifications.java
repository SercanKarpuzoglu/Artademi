package com.artademi.inventory;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Satis listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class SaleSpecifications {

    private SaleSpecifications() {
    }

    /** urunId doluysa @ManyToOne urun.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<Sale> hasUrun(Long urunId) {
        return (root, query, cb) ->
                urunId == null ? null : cb.equal(root.get("urun").get("id"), urunId);
    }

    /** ogrenciId doluysa @ManyToOne ogrenci.id esitligi; null ise filtre yok. */
    public static Specification<Sale> hasOgrenci(Long ogrenciId) {
        return (root, query, cb) ->
                ogrenciId == null ? null : cb.equal(root.get("ogrenci").get("id"), ogrenciId);
    }

    /** from doluysa satisTarihi >= from; null ise filtre yok. */
    public static Specification<Sale> tarihGte(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("satisTarihi"), from);
    }

    /** to doluysa satisTarihi <= to; null ise filtre yok. */
    public static Specification<Sale> tarihLte(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("satisTarihi"), to);
    }
}
