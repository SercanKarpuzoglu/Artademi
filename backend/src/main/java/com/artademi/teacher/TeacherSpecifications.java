package com.artademi.teacher;

import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

/**
 * Ogretmen listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class TeacherSpecifications {

    private TeacherSpecifications() {
    }

    /** aktif doluysa esitlik; null ise filtre yok (predicate null). */
    public static Specification<Teacher> hasAktif(Boolean aktif) {
        return (root, query, cb) -> aktif == null ? null : cb.equal(root.get("aktif"), aktif);
    }

    /** q doluysa ad VEYA soyad uzerinde case-insensitive contains; bos/null ise filtre yok. */
    public static Specification<Teacher> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("ad")), like),
                    cb.like(cb.lower(root.get("soyad")), like));
        };
    }

    /**
     * bransId doluysa, o branşa atanmis ogretmenler. branchLinks -> branch.id uzerinden
     * join yapilir; ayni ogretmenin birden cok baglantisi olabileceginden distinct.
     */
    public static Specification<Teacher> hasBrans(Long bransId) {
        return (root, query, cb) -> {
            if (bransId == null) {
                return null;
            }
            query.distinct(true);
            Join<Object, Object> link = root.join("branchLinks");
            return cb.equal(link.get("branch").get("id"), bransId);
        };
    }
}
