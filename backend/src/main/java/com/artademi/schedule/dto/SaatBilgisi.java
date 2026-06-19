package com.artademi.schedule.dto;

import java.time.LocalTime;

/**
 * {@code @SaatAraligiGecerli} sinif duzeyi validasyonunun ihtiyac duydugu alanlari ortaya cikaran
 * ortak arayuz. Hem {@code CreateScheduleRequest} hem {@code UpdateScheduleRequest} uygular; boylece
 * tek validator iki DTO'yu da kontrol eder (GrupBilgisi/GrupTutarli desenleriyle ayni).
 */
public interface SaatBilgisi {

    LocalTime baslangicSaati();

    LocalTime bitisSaati();
}
