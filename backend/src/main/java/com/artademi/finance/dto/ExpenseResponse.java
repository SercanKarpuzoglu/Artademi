package com.artademi.finance.dto;

import com.artademi.finance.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Gider yanit DTO'su. Entity disariya dogrudan donmez. tenant_id sizdirilmaz.
 */
public record ExpenseResponse(
        Long id,
        BigDecimal tutar,
        LocalDate giderTarihi,
        String kategori,
        String aciklama) {

    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getTutar(),
                e.getGiderTarihi(),
                e.getKategori(),
                e.getAciklama());
    }
}
