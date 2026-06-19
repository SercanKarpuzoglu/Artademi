package com.artademi.finance.dto;

import com.artademi.finance.Accrual;
import com.artademi.finance.OdemeYontemi;
import com.artademi.finance.Payment;
import com.artademi.group.Group;
import com.artademi.student.Student;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO'sunu Payment entity'sine yansitir. tenant_id BURADA ELLE yonetilmez: @PrePersist'te
 * TenantContext'ten gelir. ogrenci/accrual/grup, tenant-guvenli cozulmus entity'lerle baglanir
 * (accrual/grup null olabilir); cozumleme + capraz dogrulama servis katmaninda yapilir.
 */
public final class PaymentMapper {

    private PaymentMapper() {
    }

    /** Yeni tahsilat olusturur. accrual/grup null olabilir. */
    public static Payment toNewEntity(Student ogrenci, Accrual accrual, Group grup,
            BigDecimal tutar, LocalDate odemeTarihi, OdemeYontemi odemeYontemi, String aciklama) {
        Payment p = Payment.create();
        p.setOgrenci(ogrenci);
        p.setAccrual(accrual);
        p.setGrup(grup);
        p.setTutar(tutar);
        p.setOdemeTarihi(odemeTarihi);
        p.setOdemeYontemi(odemeYontemi);
        p.setAciklama(aciklama);
        return p;
    }
}
