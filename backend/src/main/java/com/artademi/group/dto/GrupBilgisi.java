package com.artademi.group.dto;

import com.artademi.group.GrupTipi;
import java.math.BigDecimal;

/**
 * {@code @GrupTutarli} sinif duzeyi validasyonunun ihtiyac duydugu alanlari ortaya cikaran
 * ortak arayuz. Hem {@code CreateGroupRequest} hem {@code UpdateGroupRequest} uygular; boylece
 * tek validator iki DTO'yu da kontrol eder.
 */
public interface GrupBilgisi {

    GrupTipi tip();

    Long salonId();

    BigDecimal aylikAidat();

    BigDecimal dersBasiUcret();
}
