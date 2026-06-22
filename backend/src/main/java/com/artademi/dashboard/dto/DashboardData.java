package com.artademi.dashboard.dto;

/**
 * {@code GET /api/dashboard} yanit tipi — role gore FARKLI somut tip doner. Sealed: yalnizca 4 rol
 * DTO'su izinli.
 *
 * <p><b>Guvenlik (kritik):</b> parasal alan filtresi TIP duzeyindedir — {@link FrontdeskDashboard}
 * hicbir parasal alan ICERMEZ (JSON'da o anahtarlar literal olarak yoktur, null degil). Boylece
 * "frontend gizleme" degil, backend role gore SADECE izinli alanlari serialize eder.
 */
public sealed interface DashboardData
        permits AdminDashboard, AccountingDashboard, FrontdeskDashboard, TeacherDashboard {

    String rol();
}
