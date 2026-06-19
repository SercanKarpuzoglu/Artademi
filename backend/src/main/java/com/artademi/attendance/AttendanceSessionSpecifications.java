package com.artademi.attendance;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Yoklama oturumu listesi icin dinamik (opsiyonel) filtreler. Criteria tabanli olduklari icin:
 * <ul>
 *   <li>yalnizca dolu filtre icin predicate eklenir — {@code (:param IS NULL OR ...)}
 *       anti-pattern'i ve Postgres'in untyped-null tip cikarim hatasi olusmaz;</li>
 *   <li>Criteria sorgulari da global Hibernate tenant filtresine tabidir, yani tenant
 *       izolasyonu korunur.</li>
 * </ul>
 */
public final class AttendanceSessionSpecifications {

    private AttendanceSessionSpecifications() {
    }

    /** grupId doluysa @ManyToOne grup.id esitligi; null ise filtre yok (predicate null). */
    public static Specification<AttendanceSession> hasGrup(Long grupId) {
        return (root, query, cb) ->
                grupId == null ? null : cb.equal(root.get("grup").get("id"), grupId);
    }

    /** tarih doluysa esitlik; null ise filtre yok. */
    public static Specification<AttendanceSession> hasTarih(LocalDate tarih) {
        return (root, query, cb) -> tarih == null ? null : cb.equal(root.get("tarih"), tarih);
    }

    /** from doluysa tarih >= from; null ise filtre yok. */
    public static Specification<AttendanceSession> tarihGte(LocalDate from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("tarih"), from);
    }

    /** to doluysa tarih <= to; null ise filtre yok. */
    public static Specification<AttendanceSession> tarihLte(LocalDate to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("tarih"), to);
    }

    /**
     * ogretmenId doluysa grup.ogretmen.id esitligi; null ise filtre yok. TEACHER rolu liste
     * sorgusunu yalnizca kendi gruplarina daraltmak icin kullanilir.
     */
    public static Specification<AttendanceSession> grupOgretmenId(Long ogretmenId) {
        return (root, query, cb) -> ogretmenId == null
                ? null
                : cb.equal(root.get("grup").get("ogretmen").get("id"), ogretmenId);
    }
}
