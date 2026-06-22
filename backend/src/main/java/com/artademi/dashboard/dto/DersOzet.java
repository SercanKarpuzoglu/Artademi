package com.artademi.dashboard.dto;

import java.time.LocalTime;

/** Bugunku ders ozeti (bugunun HaftaGunu'na denk aktif program; parasal alan icermez). */
public record DersOzet(String grupAd, LocalTime baslangic, LocalTime bitis, String salon) {
}
