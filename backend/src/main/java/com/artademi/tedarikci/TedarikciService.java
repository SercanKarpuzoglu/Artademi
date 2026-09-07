package com.artademi.tedarikci;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.ExpenseRepository;
import com.artademi.tedarikci.dto.TedarikciRequest;
import com.artademi.tedarikci.dto.TedarikciResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tedarikci tanimlari ve "kime ne kadar odedik" toplami.
 *
 * <p>⚠️ CARI HESAP DEGILDIR: fatura/borc-alacak yoktur, yalnizca giderlerin kime yapildigi
 * tutulur. {@code toplamOdenen} de saklanmaz, giderlerden HESAPLANIR.
 */
@Service
public class TedarikciService {

    private final TedarikciRepository repository;
    private final ExpenseRepository expenses;

    public TedarikciService(TedarikciRepository repository, ExpenseRepository expenses) {
        this.repository = repository;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<TedarikciResponse> list(Boolean aktif) {
        List<Tedarikci> liste = Boolean.TRUE.equals(aktif)
                ? repository.findByAktifTrueOrderByAdAsc()
                : repository.findAllByOrderByAdAsc();
        return liste.stream().map(t -> TedarikciResponse.from(t, toplamOdenen(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TedarikciResponse get(Long id) {
        Tedarikci t = bul(id);
        return TedarikciResponse.from(t, toplamOdenen(id));
    }

    @Transactional
    public TedarikciResponse create(TedarikciRequest req) {
        adBenzersizMi(req.ad(), null);
        Tedarikci t = Tedarikci.create();
        uygula(t, req);
        Tedarikci kayitli = repository.save(t);
        return TedarikciResponse.from(kayitli, BigDecimal.ZERO);
    }

    @Transactional
    public TedarikciResponse update(Long id, TedarikciRequest req) {
        Tedarikci t = bul(id);
        adBenzersizMi(req.ad(), id);
        uygula(t, req);
        return TedarikciResponse.from(t, toplamOdenen(id));
    }

    /** Silme YOK: gecmis giderler bagli kalir. */
    @Transactional
    public TedarikciResponse durumDegistir(Long id, boolean aktif) {
        Tedarikci t = bul(id);
        t.setAktif(aktif);
        return TedarikciResponse.from(t, toplamOdenen(id));
    }

    private BigDecimal toplamOdenen(Long id) {
        BigDecimal v = expenses.tedarikciyeOdenenToplam(id);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** ⚠️ findById DEGIL: PK-find tenant filtresine tabi degildir. */
    private Tedarikci bul(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Tedarikçi bulunamadı: " + id));
    }

    private void adBenzersizMi(String ad, Long haricId) {
        repository.findByAd(ad.trim())
                .filter(m -> !m.getId().equals(haricId))
                .ifPresent(m -> {
                    throw new ConflictException("Bu isimde bir tedarikçi zaten var: " + ad);
                });
    }

    private static void uygula(Tedarikci t, TedarikciRequest req) {
        t.setAd(req.ad().trim());
        t.setTelefon(bosaNull(req.telefon()));
        t.setEmail(bosaNull(req.email()));
        t.setVergiNo(bosaNull(req.vergiNo()));
        t.setAciklama(bosaNull(req.aciklama()));
    }

    private static String bosaNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
