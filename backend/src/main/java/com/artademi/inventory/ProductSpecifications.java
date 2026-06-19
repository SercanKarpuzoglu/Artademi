package com.artademi.inventory;

import org.springframework.data.jpa.domain.Specification;

/**
 * Urun listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** aktif doluysa esitlik; null ise filtre yok (predicate null). */
    public static Specification<Product> hasAktif(Boolean aktif) {
        return (root, query, cb) -> aktif == null ? null : cb.equal(root.get("aktif"), aktif);
    }

    /** q doluysa ad uzerinde case-insensitive contains; bos/null ise filtre yok. */
    public static Specification<Product> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("ad")), like);
        };
    }
}
