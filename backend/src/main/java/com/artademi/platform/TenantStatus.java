package com.artademi.platform;

/**
 * Tenant (kiraci) yasam durumu.
 *
 * <ul>
 *   <li>{@link #AKTIF} — normal kullanim (varsayilan).</li>
 *   <li>{@link #ASKIDA} — abonelik/odeme nedeniyle askiya alinmis (login engeli ILERIDE; bu surumde
 *       yalnizca alan tutulur).</li>
 * </ul>
 */
public enum TenantStatus {
    AKTIF,
    ASKIDA
}
