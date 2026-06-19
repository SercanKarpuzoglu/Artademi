package com.artademi.finance;

import org.springframework.data.jpa.domain.Specification;

/**
 * Tahakkuk listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class AccrualSpecifications {

    private AccrualSpecifications() {
    }

    /** ogrenciId doluysa @ManyToOne ogrenci.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<Accrual> hasOgrenci(Long ogrenciId) {
        return (root, query, cb) ->
                ogrenciId == null ? null : cb.equal(root.get("ogrenci").get("id"), ogrenciId);
    }

    /** grupId doluysa @ManyToOne grup.id esitligi; null ise filtre yok. */
    public static Specification<Accrual> hasGrup(Long grupId) {
        return (root, query, cb) ->
                grupId == null ? null : cb.equal(root.get("grup").get("id"), grupId);
    }

    /** donem doluysa esitlik; null ise filtre yok. */
    public static Specification<Accrual> hasDonem(String donem) {
        return (root, query, cb) -> donem == null ? null : cb.equal(root.get("donem"), donem);
    }
}
