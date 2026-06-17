package com.artademi.student.dto;

/**
 * {@link VeliRequired} sinif duzeyi validasyonunun ihtiyac duydugu alanlari ortaya
 * cikaran ortak arayuz. Hem {@link CreateStudentRequest} hem {@link UpdateStudentRequest}
 * uygular; boylece tek validator iki DTO'yu da kontrol eder.
 */
public interface VeliBilgisi {

    boolean yetiskinMi();

    String anneAd();

    String anneTcKimlikNo();

    String babaAd();

    String babaTcKimlikNo();
}
