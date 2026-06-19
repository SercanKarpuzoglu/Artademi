package com.artademi.finance.dto;

import com.artademi.finance.Accrual;
import com.artademi.group.Group;
import com.artademi.student.Student;
import java.math.BigDecimal;

/**
 * Request DTO'sunu Accrual entity'sine yansitir. tenant_id BURADA ELLE yonetilmez: @PrePersist'te
 * TenantContext'ten gelir. ogrenci/grup, tenant-guvenli cozulmus ({@code findScopedById}) entity'lerle
 * baglanir; cozumleme servis katmaninda yapilir, mapper yalnizca set eder (grup null olabilir).
 */
public final class AccrualMapper {

    private AccrualMapper() {
    }

    /** Yeni tahakkuk olusturur. grup null olabilir. */
    public static Accrual toNewEntity(Student ogrenci, Group grup, String donem,
            BigDecimal tutar, String aciklama) {
        Accrual a = Accrual.create();
        a.setOgrenci(ogrenci);
        a.setGrup(grup);
        a.setDonem(donem);
        a.setTutar(tutar);
        a.setAciklama(aciklama);
        return a;
    }
}
