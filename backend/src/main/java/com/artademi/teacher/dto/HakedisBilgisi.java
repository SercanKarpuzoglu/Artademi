package com.artademi.teacher.dto;

import java.util.List;

/**
 * {@code @HakedisTutarli} sinif duzeyi validasyonunun ihtiyac duydugu hakedis listesini ortaya
 * cikaran ortak arayuz. Hem {@code CreateTeacherRequest} hem {@code UpdateTeacherRequest} uygular;
 * boylece tek validator iki DTO'yu da kontrol eder (Model C: ogretmen birden cok hakedis tipi
 * tanimlar).
 */
public interface HakedisBilgisi {

    List<HakedisSatiriRequest> hakedisler();
}
