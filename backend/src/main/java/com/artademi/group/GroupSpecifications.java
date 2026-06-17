package com.artademi.group;

import org.springframework.data.jpa.domain.Specification;

/**
 * Grup listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class GroupSpecifications {

    private GroupSpecifications() {
    }

    /** tip doluysa esitlik; null ise filtre yok (predicate null). */
    public static Specification<Group> hasTip(GrupTipi tip) {
        return (root, query, cb) -> tip == null ? null : cb.equal(root.get("tip"), tip);
    }

    /** aktif doluysa esitlik; null ise filtre yok. */
    public static Specification<Group> hasAktif(Boolean aktif) {
        return (root, query, cb) -> aktif == null ? null : cb.equal(root.get("aktif"), aktif);
    }

    /** q doluysa ad uzerinde case-insensitive contains; bos/null ise filtre yok. */
    public static Specification<Group> matchesText(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("ad")), like);
        };
    }

    /** bransId doluysa @ManyToOne brans.id esitligi; null ise filtre yok. */
    public static Specification<Group> hasBrans(Long bransId) {
        return (root, query, cb) ->
                bransId == null ? null : cb.equal(root.get("brans").get("id"), bransId);
    }

    /** ogretmenId doluysa @ManyToOne ogretmen.id esitligi; null ise filtre yok. */
    public static Specification<Group> hasOgretmen(Long ogretmenId) {
        return (root, query, cb) ->
                ogretmenId == null ? null : cb.equal(root.get("ogretmen").get("id"), ogretmenId);
    }

    /** salonId doluysa @ManyToOne salon.id esitligi; null ise filtre yok. */
    public static Specification<Group> hasSalon(Long salonId) {
        return (root, query, cb) ->
                salonId == null ? null : cb.equal(root.get("salon").get("id"), salonId);
    }
}
