package com.artademi.platform;

/**
 * Tenant (kiraci) yasam durumu.
 *
 * <ul>
 *   <li>{@link #AKTIF} — normal kullanim (varsayilan).</li>
 *   <li>{@link #ASKIDA} — abonelik/odeme nedeniyle askiya alinmis; kullanicilari is uclarindan
 *       kilitlenir (403), {@code /api/me} acik. Geri alinabilir.</li>
 *   <li>{@link #SILINDI} — platform tarafindan soft-delete; listede gizli, kullanicilari kilitli
 *       (ASKIDA gibi). VERI SILINMEZ (geri alinabilir) — gercek kalici silme ayri/elle islemdir.</li>
 * </ul>
 */
public enum TenantStatus {
    AKTIF,
    ASKIDA,
    SILINDI
}
