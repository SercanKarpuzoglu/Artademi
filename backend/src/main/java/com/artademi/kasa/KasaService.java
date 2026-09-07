package com.artademi.kasa;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.finance.ExpenseRepository;
import com.artademi.finance.PaymentRepository;
import com.artademi.kasa.dto.DuzeltmeRequest;
import com.artademi.kasa.dto.HareketResponse;
import com.artademi.kasa.dto.KasaRequest;
import com.artademi.kasa.dto.KasaResponse;
import com.artademi.kasa.dto.TransferRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kasa tanimlari, bakiye ve hareketler.
 *
 * <h2>⚠️ Bakiye SAKLANMAZ, HESAPLANIR</h2>
 * Bakiye alani tutulsaydi zamanla gercekten sapardi: bir tahsilat elle duzeltilir, bir gider
 * silinir, bir guncelleme kacar ve kimse fark etmez. Bakiye her sorguda su formulle uretilir:
 * <pre>acilis + tahsilatlar - giderler + hareket girisleri - hareket cikislari</pre>
 *
 * <p>Tahsilat/gider {@code kasa_hareketi} tablosunda DEGILDIR; kendi tablolarinda durur ve
 * kasaya {@code kasa_id} ile baglidir. Ikisine birden yazilsaydi ayni para iki kez sayilirdi.
 */
@Service
public class KasaService {

    private final KasaRepository repository;
    private final KasaHareketiRepository hareketler;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;

    public KasaService(KasaRepository repository, KasaHareketiRepository hareketler,
            PaymentRepository payments, ExpenseRepository expenses) {
        this.repository = repository;
        this.hareketler = hareketler;
        this.payments = payments;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<KasaResponse> list(Boolean aktif) {
        List<Kasa> kasalar = Boolean.TRUE.equals(aktif)
                ? repository.findByAktifTrueOrderByAdAsc()
                : repository.findAllByOrderByAdAsc();
        return kasalar.stream().map(k -> KasaResponse.from(k, bakiye(k.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public KasaResponse get(Long id) {
        Kasa k = bul(id);
        return KasaResponse.from(k, bakiye(k.getId()));
    }

    /**
     * Kasanin guncel bakiyesi.
     *
     * <p>Dort bilesenin toplami; hicbiri saklanmaz. Bos toplamlar {@code null} doner,
     * burada sifira cevrilir.
     */
    @Transactional(readOnly = true)
    public BigDecimal bakiye(Long kasaId) {
        Kasa k = bul(kasaId);
        return k.getAcilisBakiyesi()
                .add(sifirSaOlmaz(payments.kasayaGirenToplam(kasaId)))
                .subtract(sifirSaOlmaz(expenses.kasadanCikanToplam(kasaId)))
                .add(sifirSaOlmaz(hareketler.netHareket(kasaId)));
    }

    @Transactional
    public KasaResponse create(KasaRequest req) {
        adBenzersizMi(req.ad(), null);
        Kasa k = Kasa.create();
        uygula(k, req);
        Kasa kayitli = repository.save(k);
        return KasaResponse.from(kayitli, bakiye(kayitli.getId()));
    }

    @Transactional
    public KasaResponse update(Long id, KasaRequest req) {
        Kasa k = bul(id);
        adBenzersizMi(req.ad(), id);
        uygula(k, req);
        return KasaResponse.from(k, bakiye(id));
    }

    /**
     * Kasayi pasiflestirir/aktiflestirir.
     *
     * <p>Silme YOK: gecmis tahsilat ve giderler bu kasaya bagli kalmaya devam eder; kaydi
     * silmek o gecmisi sahipsiz birakirdi.
     */
    @Transactional
    public KasaResponse durumDegistir(Long id, boolean aktif) {
        Kasa k = bul(id);
        k.setAktif(aktif);
        return KasaResponse.from(k, bakiye(id));
    }

    // ---------- hareketler ----------

    @Transactional(readOnly = true)
    public List<HareketResponse> hareketler(Long kasaId) {
        bul(kasaId); // yabanci/olmayan kasa -> 404
        return hareketler.findByKasaIdOrderByTarihDescIdDesc(kasaId).stream()
                .map(HareketResponse::from).toList();
    }

    /**
     * Kasalar arasi transfer — IKI hareket satiri uretir, tek islemde.
     *
     * <p>Ortak {@code transferGrubu} ile baglanirlar; boylece bakiye sorgusu duz bir
     * toplam kalir ve silme grup uzerinden yapilip yarim transfer birakmaz.
     */
    @Transactional
    public List<HareketResponse> transfer(TransferRequest req) {
        if (req.kaynakKasaId().equals(req.hedefKasaId())) {
            throw new ValidationException("Kaynak ve hedef kasa aynı olamaz.");
        }
        Kasa kaynak = bul(req.kaynakKasaId());
        Kasa hedef = bul(req.hedefKasaId());

        UUID grup = UUID.randomUUID();
        KasaHareketi cikis = hareketler.save(KasaHareketi.of(kaynak, HareketYonu.CIKIS,
                HareketTipi.TRANSFER, req.tutar(), req.tarihOrBugun(), req.aciklama(), grup));
        KasaHareketi giris = hareketler.save(KasaHareketi.of(hedef, HareketYonu.GIRIS,
                HareketTipi.TRANSFER, req.tutar(), req.tarihOrBugun(), req.aciklama(), grup));

        return List.of(HareketResponse.from(cikis), HareketResponse.from(giris));
    }

    /** Elle duzeltme (sayim farki, banka masrafi…). */
    @Transactional
    public HareketResponse duzeltme(Long kasaId, DuzeltmeRequest req) {
        Kasa k = bul(kasaId);
        KasaHareketi h = hareketler.save(KasaHareketi.of(k, req.yon(), HareketTipi.DUZELTME,
                req.tutar(), req.tarihOrBugun(), req.aciklama(), null));
        return HareketResponse.from(h);
    }

    /**
     * Hareketi siler. Transferse IKI bacak birden silinir — tek bacagi silmek kasalar
     * arasinda kaybolmus para birakirdi.
     */
    @Transactional
    public void hareketSil(Long hareketId) {
        KasaHareketi h = hareketler.findScopedById(hareketId)
                .orElseThrow(() -> new NotFoundException("Hareket bulunamadı: " + hareketId));
        if (h.getTransferGrubu() != null) {
            hareketler.deleteAll(hareketler.findByTransferGrubu(h.getTransferGrubu()));
        } else {
            hareketler.delete(h);
        }
    }

    // ---------- yardimcilar ----------

    /** ⚠️ findById DEGIL: PK-find tenant filtresine tabi degildir. */
    private Kasa bul(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Kasa bulunamadı: " + id));
    }

    private void adBenzersizMi(String ad, Long haricId) {
        repository.findByAd(ad.trim())
                .filter(mevcut -> !mevcut.getId().equals(haricId))
                .ifPresent(mevcut -> {
                    throw new ConflictException("Bu isimde bir kasa zaten var: " + ad);
                });
    }

    private static void uygula(Kasa k, KasaRequest req) {
        k.setAd(req.ad().trim());
        k.setTip(req.tip());
        k.setIban(req.iban() == null || req.iban().isBlank() ? null : req.iban().trim());
        k.setAcilisBakiyesi(req.acilisBakiyesiOrZero());
    }

    private static BigDecimal sifirSaOlmaz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
