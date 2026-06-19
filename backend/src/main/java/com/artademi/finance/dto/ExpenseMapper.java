package com.artademi.finance.dto;

import com.artademi.finance.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO'sunu Expense entity'sine yansitir. tenant_id BURADA ELLE yonetilmez: @PrePersist'te
 * TenantContext'ten gelir.
 */
public final class ExpenseMapper {

    private ExpenseMapper() {
    }

    /** Yeni gider olusturur. */
    public static Expense toNewEntity(BigDecimal tutar, LocalDate giderTarihi,
            String kategori, String aciklama) {
        Expense e = Expense.create();
        e.setTutar(tutar);
        e.setGiderTarihi(giderTarihi);
        e.setKategori(kategori);
        e.setAciklama(aciklama);
        return e;
    }
}
