package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import java.math.BigDecimal;

/**
 * {@code @HakedisTutarli} sinif duzeyi validasyonunun ihtiyac duydugu alanlari ortaya cikaran
 * ortak arayuz. Hem {@code CreateTeacherRequest} hem {@code UpdateTeacherRequest} uygular;
 * boylece tek validator iki DTO'yu da kontrol eder.
 */
public interface HakedisBilgisi {

    HakedisTipi hakedisTipi();

    BigDecimal saatlikUcret();

    BigDecimal ciroOrani();
}
