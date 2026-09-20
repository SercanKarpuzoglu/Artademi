package com.artademi.finance;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.finance.dto.CreatePaymentRequest;
import com.artademi.finance.dto.IadeOnizleme;
import com.artademi.finance.dto.IadeRequest;
import com.artademi.finance.dto.PaymentMapper;
import com.artademi.finance.dto.PaymentResponse;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tahsilat (payment) is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir
 * (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen ogrenciId ZORUNLU; accrualId/grupId (varsa)
 * ilgili repository'nin {@code findScopedById} metodu ile cozulur; bulunamazsa -> 404.
 *
 * <p>Is kurali: accrual verilirse tahakkugun ogrencisi payment ogrencisi ile AYNI olmalidir; degilse
 * {@link ValidationException} (-> 400).
 *
 * <p>{@code odemeTarihi} verilmezse bugun (LocalDate.now()) kullanilir. tutar pozitifligi DTO @Positive
 * ile (-> 400).
 *
 * <p>Silme YOK.
 */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final AccrualRepository accrualRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final com.artademi.kasa.KasaRepository kasaRepository;
    private final com.artademi.paket.PaketService paketService;

    public PaymentService(PaymentRepository repository, AccrualRepository accrualRepository,
            StudentRepository studentRepository, GroupRepository groupRepository,
            com.artademi.kasa.KasaRepository kasaRepository,
            com.artademi.paket.PaketService paketService) {
        this.repository = repository;
        this.accrualRepository = accrualRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.kasaRepository = kasaRepository;
        this.paketService = paketService;
    }

    /** Yeni tahsilat olusturur, 201. */
    @Transactional
    public PaymentResponse create(CreatePaymentRequest req) {
        Student ogrenci = resolveStudent(req.ogrenciId());
        Group grup = req.grupId() == null ? null : resolveGroup(req.grupId());

        Accrual accrual = null;
        if (req.accrualId() != null) {
            accrual = accrualRepository.findScopedById(req.accrualId())
                    .orElseThrow(() -> new NotFoundException("Tahakkuk bulunamadı: " + req.accrualId()));
            // Tahakkuk farkli ogrenciye aitse tahsilat ona baglanamaz -> 400.
            if (!accrual.getOgrenci().getId().equals(ogrenci.getId())) {
                throw new ValidationException("Tahakkuk bu öğrenciye ait değil");
            }
        }

        LocalDate odemeTarihi = req.odemeTarihi() != null ? req.odemeTarihi() : LocalDate.now();
        Payment yeni = PaymentMapper.toNewEntity(
                ogrenci, accrual, grup, req.tutar(), odemeTarihi, req.odemeYontemi(), req.aciklama());
        yeni.setKasa(resolveKasa(req.kasaId()));
        Payment saved = repository.save(yeni);
        return PaymentResponse.from(saved);
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

    // =====================================================================
    // Iade (V37)
    // =====================================================================

    /**
     * Tahsilat iadesi: orijinal satira DOKUNULMAZ, NEGATIF tutarli yeni bir satir yazilir.
     *
     * <p><b>Neden silme degil:</b> silmek "bu para hic alinmadi" demektir; oysa para alindi VE geri
     * verildi — velinin makbuzu, kasadaki giris ve cikis gercektir. Silseydik kasa da yanlis olurdu
     * (para fiilen cikti ama biz giris satirini yok ettik). Ayrica kismi iade silmeyle yapilamaz.
     *
     * <p>Negatif satir sayesinde ogrenci bakiyesi, kasa bakiyesi ve Gelirler ozeti — ucu de SUM()
     * ile calistigindan — kendiliginden duzelir; hicbir toplam sorgusu degismedi.
     *
     * <p><b>Kredi:</b> iade, ogrencinin kalan kredisini IPTAL eder (urun karari 2026-09-20; bkz.
     * {@code PaketService.iadeSonrasiKrediIptali}). Parayi geri verip kontorleri birakmak bedava
     * ders vermektir. Ne kadar kredinin gidecegi iade-onizleme ucunda ONCEDEN gosterilir.
     *
     * <p>Ayni islemde: iade satiri + kredi iptali. Biri patlarsa ikisi de geri alinir.
     */
    @Transactional
    public PaymentResponse iade(Long odemeId, IadeRequest req) {
        Payment orijinal = findOrThrow(odemeId);
        String engel = iadeEngeli(orijinal, req.tutar());
        if (engel != null) {
            throw com.artademi.common.exception.ValidationException.alan("tutar", engel);
        }

        Payment iade = PaymentMapper.toNewEntity(
                orijinal.getOgrenci(),
                orijinal.getAccrual(),
                orijinal.getGrup(),
                req.tutar().negate(),
                req.iadeTarihi() != null ? req.iadeTarihi() : LocalDate.now(),
                req.odemeYontemi() != null ? req.odemeYontemi() : orijinal.getOdemeYontemi(),
                req.aciklama());
        // Kasa verilmezse parayi aldigimiz kasadan geri veririz; nakit alinip havaleyle iade
        // edilebildigi icin degistirilebilir.
        iade.setKasa(req.kasaId() != null ? resolveKasa(req.kasaId()) : orijinal.getKasa());
        iade.setIadeEdilenOdeme(orijinal);
        Payment saved = repository.save(iade);

        paketService.iadeSonrasiKrediIptali(
                orijinal.getOgrenci().getId(),
                orijinal.getGrup() == null ? null : orijinal.getGrup().getId());
        return PaymentResponse.from(saved);
    }

    /** Iade ekraninin onay oncesi gosterdigi ozet: ne kadar iade edilebilir, ne kadar kredi gider. */
    @Transactional(readOnly = true)
    public IadeOnizleme iadeOnizleme(Long odemeId) {
        Payment orijinal = findOrThrow(odemeId);
        BigDecimal iadeEdilen = repository.iadeToplami(odemeId);
        BigDecimal kalan = orijinal.getTutar().subtract(iadeEdilen);
        var kredi = paketService.iadeKrediOzeti(
                orijinal.getOgrenci().getId(),
                orijinal.getGrup() == null ? null : orijinal.getGrup().getId());
        return new IadeOnizleme(
                odemeId,
                orijinal.getTutar(),
                iadeEdilen,
                kalan.max(BigDecimal.ZERO),
                kredi.paketSayisi(),
                kredi.kalanKontor(),
                iadeEngeli(orijinal, null));
    }

    /**
     * Iadeyi engelleyen sebep; engel yoksa {@code null}.
     *
     * @param tutar iade edilmek istenen tutar; {@code null} ise yalnizca kaydin iadeye uygunlugu
     *              kontrol edilir (onizleme, tutar girilmeden once cagirir)
     */
    private String iadeEngeli(Payment orijinal, BigDecimal tutar) {
        // Iadenin iadesi: ust uste ters kayit zinciri kurulursa hangi paranin geri verildigi
        // takip edilemez hale gelir. Fazla iade edildiyse yeni bir TAHSILAT girilir.
        if (orijinal.isIade()) {
            return "Bu kayıt zaten bir iade; iadenin iadesi yapılamaz.";
        }
        BigDecimal iadeEdilen = repository.iadeToplami(orijinal.getId());
        BigDecimal kalan = orijinal.getTutar().subtract(iadeEdilen);
        if (kalan.signum() <= 0) {
            return "Bu tahsilatın tamamı zaten iade edilmiş.";
        }
        if (tutar != null && tutar.compareTo(kalan) > 0) {
            return "İade tutarı kalan iade edilebilir tutarı (" + kalan + " ₺) aşamaz.";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(findOrThrow(id));
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(Long ogrenciId, LocalDate from, LocalDate to,
            OdemeYontemi yontem, Pageable pageable) {
        Specification<Payment> spec = Specification
                .where(PaymentSpecifications.hasOgrenci(ogrenciId))
                .and(PaymentSpecifications.tarihGte(from))
                .and(PaymentSpecifications.tarihLte(to))
                .and(PaymentSpecifications.hasYontem(yontem));
        return repository.findAll(spec, pageable)
                .map(PaymentResponse::from);
    }

    private Student resolveStudent(Long ogrenciId) {
        return studentRepository.findScopedById(ogrenciId)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + ogrenciId));
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Payment findOrThrow(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Tahsilat bulunamadı: " + id));
    }
}
