package com.artademi.finance;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Tahsilat listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    /** ogrenciId doluysa @ManyToOne ogrenci.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<Payment> hasOgrenci(Long ogrenciId) {
        return (root, query, cb) ->
                ogrenciId == null ? null : cb.equal(root.get("ogrenci").get("id"), ogrenciId);
    }

    /** yontem doluysa esitlik; null ise filtre yok. */
    public static Specification<Payment> hasYontem(OdemeYontemi yontem) {
        return (root, query, cb) -> yontem == null ? null : cb.equal(root.get("odemeYontemi"), yontem);
    }

    /** from doluysa odemeTarihi >= from; null ise filtre yok. */
    public static Specification<Payment> tarihGte(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("odemeTarihi"), from);
    }

    /** to doluysa odemeTarihi <= to; null ise filtre yok. */
    public static Specification<Payment> tarihLte(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("odemeTarihi"), to);
    }
}
