package com.artademi.payout;

import org.springframework.data.jpa.domain.Specification;

/**
 * Hakedis (payout) listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class PayoutSpecifications {

    private PayoutSpecifications() {
    }

    /** ogretmenId doluysa @ManyToOne ogretmen.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<Payout> hasOgretmen(Long ogretmenId) {
        return (root, query, cb) ->
                ogretmenId == null ? null : cb.equal(root.get("ogretmen").get("id"), ogretmenId);
    }

    /** donem doluysa esitlik; null ise filtre yok. */
    public static Specification<Payout> hasDonem(String donem) {
        return (root, query, cb) -> donem == null ? null : cb.equal(root.get("donem"), donem);
    }

    /** durum doluysa esitlik; null ise filtre yok. */
    public static Specification<Payout> hasDurum(PayoutDurumu durum) {
        return (root, query, cb) -> durum == null ? null : cb.equal(root.get("durum"), durum);
    }
}
