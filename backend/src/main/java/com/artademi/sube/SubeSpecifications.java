package com.artademi.sube;

import org.springframework.data.jpa.domain.Specification;

/**
 * Sube listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin yalnizca
 * dolu filtre icin predicate eklenir ve Criteria sorgulari da global tenant filtresine tabidir.
 */
public final class SubeSpecifications {

    private SubeSpecifications() {
    }

    /** aktif doluysa esitlik; null ise filtre yok. */
    public static Specification<Sube> hasAktif(Boolean aktif) {
        return (root, query, cb) -> aktif == null ? null : cb.equal(root.get("aktif"), aktif);
    }

    /** q doluysa ad uzerinde case-insensitive contains; bos/null ise filtre yok. */
    public static Specification<Sube> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("ad")), like);
        };
    }
}
