package com.artademi.dashboard.dto;

import com.artademi.finance.OdemeYontemi;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Son odeme ozeti — PARASAL; yalnizca ADMIN ve FRONTDESK_ACCOUNTING DTO'larinda yer alir. */
public record OdemeOzet(String ogrenciAd, BigDecimal tutar, LocalDate tarih, OdemeYontemi yontem) {
}
