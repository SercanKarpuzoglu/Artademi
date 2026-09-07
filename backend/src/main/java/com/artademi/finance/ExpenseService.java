package com.artademi.finance;

import com.artademi.common.exception.NotFoundException;
import com.artademi.finance.dto.CreateExpenseRequest;
import com.artademi.finance.dto.ExpenseMapper;
import com.artademi.finance.dto.ExpenseResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gider (expense) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>{@code giderTarihi} verilmezse bugun (LocalDate.now()) kullanilir. tutar pozitifligi DTO
 * @Positive ile (-> 400). Gider ogrenci/gruba bagli DEGIL.
 *
 * <p>Silme YOK.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final com.artademi.kasa.KasaRepository kasaRepository;
    private final com.artademi.tedarikci.TedarikciRepository tedarikciRepository;

    public ExpenseService(ExpenseRepository repository,
            com.artademi.kasa.KasaRepository kasaRepository,
            com.artademi.tedarikci.TedarikciRepository tedarikciRepository) {
        this.repository = repository;
        this.kasaRepository = kasaRepository;
        this.tedarikciRepository = tedarikciRepository;
    }

    /**
     * Kasa/tedarikci secildiyse AYNI tenant'a ait oldugunu dogrular.
     *
     * <p>⚠️ findScopedById: FK tek basina yabanci referansi engellemez; istemci baska kurumun
     * id'sini gondererek capraz-tenant bag kuramamalidir.
     */
    private com.artademi.kasa.Kasa resolveKasa(Long kasaId) {
        if (kasaId == null) {
            return null;
        }
        return kasaRepository.findScopedById(kasaId)
                .orElseThrow(() -> new NotFoundException("Kasa bulunamadı: " + kasaId));
    }

    private com.artademi.tedarikci.Tedarikci resolveTedarikci(Long tedarikciId) {
        if (tedarikciId == null) {
            return null;
        }
        return tedarikciRepository.findScopedById(tedarikciId)
                .orElseThrow(() -> new NotFoundException("Tedarikçi bulunamadı: " + tedarikciId));
    }

    /** Yeni gider olusturur, 201. */
    @Transactional
    public ExpenseResponse create(CreateExpenseRequest req) {
        LocalDate giderTarihi = req.giderTarihi() != null ? req.giderTarihi() : LocalDate.now();
        Expense yeni = ExpenseMapper.toNewEntity(
                req.tutar(), giderTarihi, req.kategori(), req.aciklama());
        yeni.setKasa(resolveKasa(req.kasaId()));
        yeni.setTedarikci(resolveTedarikci(req.tedarikciId()));
        Expense saved = repository.save(yeni);
        return ExpenseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        return ExpenseResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> search(LocalDate from, LocalDate to, String kategori, Pageable pageable) {
        Specification<Expense> spec = Specification
                .where(ExpenseSpecifications.tarihGte(from))
                .and(ExpenseSpecifications.tarihLte(to))
                .and(ExpenseSpecifications.kategoriContains(kategori));
        return repository.findAll(spec, pageable)
                .map(ExpenseResponse::from);
    }

    private Expense findOrThrow(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Gider bulunamadı: " + id));
    }
}
