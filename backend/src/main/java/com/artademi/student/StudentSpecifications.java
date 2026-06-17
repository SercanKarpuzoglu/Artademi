package com.artademi.student;

import org.springframework.data.jpa.domain.Specification;

/**
 * Ogrenci listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi ({@code lower(bytea)})
 *       olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    /** status doluysa esitlik; null ise filtre yok (predicate null). */
    public static Specification<Student> hasStatus(StudentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** q doluysa ad/soyad/tc uzerinde case-insensitive contains; bos/null ise filtre yok. */
    public static Specification<Student> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("ad")), like),
                    cb.like(cb.lower(root.get("soyad")), like),
                    cb.like(cb.lower(root.get("tcKimlikNo")), like));
        };
    }
}
