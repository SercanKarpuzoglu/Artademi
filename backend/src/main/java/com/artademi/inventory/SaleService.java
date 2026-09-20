package com.artademi.inventory;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.inventory.dto.CreateSaleRequest;
import com.artademi.inventory.dto.SatisIadeOnizleme;
import com.artademi.inventory.dto.SatisIadeRequest;
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
    private final com.artademi.kasa.KasaRepository kasaRepository;

    public SaleService(SaleRepository repository, ProductRepository productRepository,
            StudentRepository studentRepository,
            com.artademi.kasa.KasaRepository kasaRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.studentRepository = studentRepository;
        this.kasaRepository = kasaRepository;
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

        Sale yeni = SaleMapper.toNewEntity(
                urun, ogrenci, adet, birimFiyat, toplamTutar, satisTarihi, req.aciklama());
        yeni.setKasa(resolveKasa(req.kasaId()));
        return SaleResponse.from(repository.save(yeni));
    }

    // =====================================================================
    // Iade (V37)
    // =====================================================================

    /**
     * Urun iadesi: orijinal satira DOKUNULMAZ, NEGATIF adet/tutarli yeni bir satir yazilir ve
     * stok geri eklenir.
     *
     * <p><b>Neden silme degil:</b> satisi silmek de stogu geri ekler (bkz. SilmeService) ama
     * "bu satis hic olmadi" demektir; Gelirler'den ve kasadan parayi iz birakmadan siler. Oysa
     * urun satildi VE parasi geri verildi. Ayrica 3 adetten 1'ini iade etmek silmeyle yapilamaz.
     *
     * <p>Birim fiyat ORIJINAL satistan kopyalanir: urunun fiyati sonradan degistiyse veliye
     * satin aldigi fiyat geri verilir, gunun fiyati degil.
     *
     * <p>Ayni islemde: iade satiri + stok iadesi. Biri patlarsa ikisi de geri alinir.
     */
    @Transactional
    public SaleResponse iade(Long satisId, SatisIadeRequest req) {
        Sale orijinal = findOrThrow(satisId);
        String engel = iadeEngeli(orijinal, req.adet());
        if (engel != null) {
            throw ValidationException.alan("adet", engel);
        }

        int adet = req.adet();
        BigDecimal birimFiyat = orijinal.getBirimFiyat();
        BigDecimal toplamTutar = birimFiyat.multiply(BigDecimal.valueOf(-adet))
                .setScale(2, RoundingMode.HALF_UP);

        // Stok geri eklenir (ayni transaction, atomik).
        Product urun = orijinal.getUrun();
        urun.setStokAdedi(urun.getStokAdedi() + adet);

        Sale iade = SaleMapper.toNewEntity(
                urun,
                orijinal.getOgrenci(),
                -adet,
                birimFiyat,
                toplamTutar,
                req.iadeTarihi() != null ? req.iadeTarihi() : LocalDate.now(),
                req.aciklama());
        // Kasa verilmezse parayi aldigimiz kasadan geri veririz.
        iade.setKasa(req.kasaId() != null ? resolveKasa(req.kasaId()) : orijinal.getKasa());
        iade.setIadeEdilenSatis(orijinal);
        return SaleResponse.from(repository.save(iade));
    }

    /** Iade onay ekraninin ozeti: kac adet iade edilebilir, hangi fiyattan, engel var mi. */
    @Transactional(readOnly = true)
    public SatisIadeOnizleme iadeOnizleme(Long satisId) {
        Sale orijinal = findOrThrow(satisId);
        int iadeEdilen = repository.iadeEdilenAdet(satisId);
        return new SatisIadeOnizleme(
                satisId,
                orijinal.getAdet(),
                iadeEdilen,
                Math.max(0, orijinal.getAdet() - iadeEdilen),
                orijinal.getBirimFiyat(),
                iadeEngeli(orijinal, null));
    }

    /**
     * Iadeyi engelleyen sebep; engel yoksa {@code null}.
     *
     * @param adet iade edilmek istenen adet; {@code null} ise yalnizca kaydin iadeye uygunlugu
     *             kontrol edilir (onizleme, adet girilmeden once cagirir)
     */
    private String iadeEngeli(Sale orijinal, Integer adet) {
        if (orijinal.isIade()) {
            return "Bu kayıt zaten bir iade; iadenin iadesi yapılamaz.";
        }
        int kalan = orijinal.getAdet() - repository.iadeEdilenAdet(orijinal.getId());
        if (kalan <= 0) {
            return "Bu satışın tamamı zaten iade edilmiş.";
        }
        if (adet != null && adet > kalan) {
            return "İade adedi kalan iade edilebilir adedi (" + kalan + ") aşamaz.";
        }
        return null;
    }

    /**
     * Kasa secildiyse AYNI tenant'a ait oldugunu dogrular.
     *
     * <p>⚠️ findScopedById: FK tek basina yabanci kasa referansini engellemez; istemci baska
     * kurumun kasa id'sini gondererek capraz-tenant bag kuramamalidir.
     */
    private com.artademi.kasa.Kasa resolveKasa(Long kasaId) {
        if (kasaId == null) {
            return null;
        }
        return kasaRepository.findScopedById(kasaId)
                .orElseThrow(() -> new NotFoundException("Kasa bulunamadı: " + kasaId));
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
