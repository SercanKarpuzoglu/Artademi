package com.artademi.schedule;

import org.springframework.data.jpa.domain.Specification;

/**
 * Program listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class ScheduleSpecifications {

    private ScheduleSpecifications() {
    }

    /** grupId doluysa @ManyToOne grup.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<Schedule> hasGrup(Long grupId) {
        return (root, query, cb) ->
                grupId == null ? null : cb.equal(root.get("grup").get("id"), grupId);
    }

    /** gun doluysa esitlik; null ise filtre yok. */
    public static Specification<Schedule> hasGun(HaftaGunu gun) {
        return (root, query, cb) -> gun == null ? null : cb.equal(root.get("gun"), gun);
    }

    /** aktif doluysa esitlik; null ise filtre yok. */
    public static Specification<Schedule> hasAktif(Boolean aktif) {
        return (root, query, cb) -> aktif == null ? null : cb.equal(root.get("aktif"), aktif);
    }
}
