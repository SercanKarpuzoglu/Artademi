package com.artademi.finance;

import com.artademi.common.exception.ValidationException;
import com.artademi.finance.dto.GelirOzetiResponse;
import com.artademi.inventory.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gelirler ozeti (Dalga A: "Odemeler sekmesi Gelirler olsun, urun satis gelirleri de burada").
 * Iki ayri toplam sorgusu (odeme + satis), tek yanit. {@code @Transactional} oldugundan global
 * tenant filtresi aktif oturumda calisir; toplamlar yalnizca aktif tenant'a aittir.
 *
 * <p>PARA KURALI: {@link BigDecimal}, scale 2, {@link RoundingMode#HALF_UP}.
 */
@Service
public class GelirOzetiService {

    private final PaymentRepository paymentRepository;
    private final SaleRepository saleRepository;

    public GelirOzetiService(PaymentRepository paymentRepository, SaleRepository saleRepository) {
        this.paymentRepository = paymentRepository;
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public GelirOzetiResponse ozet(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("Başlangıç ve bitiş tarihi zorunludur");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("Bitiş tarihi başlangıçtan önce olamaz");
        }
        BigDecimal odeme = paymentRepository.sumTutarByTarihAraligi(from, to).setScale(2, RoundingMode.HALF_UP);
        BigDecimal satis = saleRepository.sumToplamTutarByTarihAraligi(from, to).setScale(2, RoundingMode.HALF_UP);
        return new GelirOzetiResponse(from, to, odeme, satis, odeme.add(satis));
    }
}
