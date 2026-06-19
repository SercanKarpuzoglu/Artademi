package com.artademi.finance;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Gider listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    /** from doluysa giderTarihi >= from; null ise filtre yok. */
    public static Specification<Expense> tarihGte(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("giderTarihi"), from);
    }

    /** to doluysa giderTarihi <= to; null ise filtre yok. */
    public static Specification<Expense> tarihLte(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("giderTarihi"), to);
    }

    /** kategori doluysa buyuk/kucuk harf duyarsiz icerir (contains); null/bos ise filtre yok. */
    public static Specification<Expense> kategoriContains(String kategori) {
        return (root, query, cb) -> (kategori == null || kategori.isBlank())
                ? null
                : cb.like(cb.lower(root.get("kategori")), "%" + kategori.toLowerCase() + "%");
    }
}
