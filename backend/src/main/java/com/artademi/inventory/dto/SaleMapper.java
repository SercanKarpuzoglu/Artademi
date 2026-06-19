package com.artademi.inventory.dto;

import com.artademi.inventory.Product;
import com.artademi.inventory.Sale;
import com.artademi.student.Student;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO'sunu Sale entity'sine yansitir. tenant_id BURADA ELLE yonetilmez: @PrePersist'te
 * TenantContext'ten gelir. urun/ogrenci, tenant-guvenli cozulmus entity'lerle baglanir (ogrenci
 * null olabilir); cozumleme, birimFiyat kopyalama, toplamTutar hesabi ve stok dusumu servis
 * katmaninda yapilir — mapper yalnizca set eder.
 */
public final class SaleMapper {

    private SaleMapper() {
    }

    /** Yeni satis olusturur. ogrenci null olabilir. birimFiyat/toplamTutar serviste hesaplanir. */
    public static Sale toNewEntity(Product urun, Student ogrenci, int adet, BigDecimal birimFiyat,
            BigDecimal toplamTutar, LocalDate satisTarihi, String aciklama) {
        Sale s = Sale.create();
        s.setUrun(urun);
        s.setOgrenci(ogrenci);
        s.setAdet(adet);
        s.setBirimFiyat(birimFiyat);
        s.setToplamTutar(toplamTutar);
        s.setSatisTarihi(satisTarihi);
        s.setAciklama(aciklama);
        return s;
    }
}
