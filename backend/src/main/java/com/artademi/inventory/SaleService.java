package com.artademi.inventory;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.inventory.dto.CreateSaleRequest;
import com.artademi.inventory.dto.SaleMapper;
import com.artademi.inventory.dto.SaleResponse;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satis is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant filtresi aktif
 * oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir (bkz.
 * TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen urunId ZORUNLU; ogrenciId (varsa) ilgili
 * repository'nin {@code findScopedById} metodu ile cozulur; bulunamazsa -> 404. Boylece baska
 * tenant'in urun/ogrenci id'siyle satis yapilamaz (sizinti yok).
 *
 * <p>STOK + PARA KURALI (ayni transaction, atomik): once stok yeterliligi kontrol edilir; stok adetten
 * az ise {@link ConflictException} (-> 409 "Yetersiz stok") — satir OLUSMAZ, stok DEGISMEZ. Yeterliyse
 * birimFiyat urunun guncel satisFiyati'ndan KOPYALANIR (sonradan fiyat degisse bile sabit kalir),
 * toplamTutar = birimFiyat * adet (scale 2, HALF_UP), urun stogu adet kadar dusurulur ve satis kaydedilir.
 *
 * <p>{@code satisTarihi} verilmezse bugun (LocalDate.now()) kullanilir. adet pozitifligi DTO @Positive
 * ile (-> 400).
 *
 * <p>Satis DEGISMEZ ve SILINMEZ.
 */
@Service
public class SaleService {

    private final SaleRepository repository;
    private final ProductRepository productRepository;
    private final StudentRepository studentRepository;

    public SaleService(SaleRepository repository, ProductRepository productRepository,
            StudentRepository studentRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.studentRepository = studentRepository;
    }

    /** Yeni satis olusturur; stok yeterliyse stogu dusurur (atomik), 201. */
    @Transactional
    public SaleResponse create(CreateSaleRequest req) {
        Product urun = productRepository.findScopedById(req.urunId())
                .orElseThrow(() -> new NotFoundException("Ürün bulunamadı: " + req.urunId()));
        Student ogrenci = req.ogrenciId() == null ? null : resolveStudent(req.ogrenciId());

        int adet = req.adet();
        // Stok yeterliligi (ayni transaction): yetersizse satir olusmaz, stok degismez.
        if (urun.getStokAdedi() < adet) {
            throw new ConflictException("Yetersiz stok");
        }

        // birimFiyat satis aninda KOPYALANIR (urun fiyati sonradan degisse bile sabit kalir).
        BigDecimal birimFiyat = urun.getSatisFiyati();
        BigDecimal toplamTutar = birimFiyat.multiply(BigDecimal.valueOf(adet)).setScale(2, RoundingMode.HALF_UP);
        LocalDate satisTarihi = req.satisTarihi() != null ? req.satisTarihi() : LocalDate.now();

        // Stok dusumu (ayni transaction, atomik).
        urun.setStokAdedi(urun.getStokAdedi() - adet);

        Sale saved = repository.save(SaleMapper.toNewEntity(
                urun, ogrenci, adet, birimFiyat, toplamTutar, satisTarihi, req.aciklama()));
        return SaleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return SaleResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<SaleResponse> search(Long urunId, Long ogrenciId, LocalDate from, LocalDate to,
            Pageable pageable) {
        Specification<Sale> spec = Specification
                .where(SaleSpecifications.hasUrun(urunId))
                .and(SaleSpecifications.hasOgrenci(ogrenciId))
                .and(SaleSpecifications.tarihGte(from))
                .and(SaleSpecifications.tarihLte(to));
        return repository.findAll(spec, pageable)
                .map(SaleResponse::from);
    }

    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Sale findOrThrow(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Satış bulunamadı: " + id));
    }
}
