package com.artademi.basvuru;

import com.artademi.basvuru.dto.BasvuruResponse;
import com.artademi.basvuru.dto.OgrenciyeDonusturRequest;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.student.dto.CreateStudentRequest;
import com.artademi.student.dto.StudentMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kurum ici basvuru yonetimi (liste, durum, ogrenciye donusturme).
 *
 * <p>Silme YOK: basvuru {@link BasvuruDurumu} ile takip edilir; kimin ne zaman basvurdugunun
 * izi korunur.
 */
@Service
public class BasvuruService {

    private final BasvuruRepository repository;
    private final StudentRepository students;

    public BasvuruService(BasvuruRepository repository, StudentRepository students) {
        this.repository = repository;
        this.students = students;
    }

    /** Basvuru listesi; {@code durum} verilirse ona gore filtreler. En yeni once. */
    @Transactional(readOnly = true)
    public Page<BasvuruResponse> list(BasvuruDurumu durum, Pageable pageable) {
        Page<Basvuru> sayfa = durum == null
                ? repository.findAllByOrderByOlusturulmaTarihiDesc(pageable)
                : repository.findByDurumOrderByOlusturulmaTarihiDesc(durum, pageable);
        return sayfa.map(BasvuruResponse::from);
    }

    /** Panel rozeti: henuz ilgilenilmemis basvuru sayisi. */
    @Transactional(readOnly = true)
    public long yeniSayisi() {
        return repository.countByDurum(BasvuruDurumu.YENI);
    }

    @Transactional(readOnly = true)
    public BasvuruResponse get(Long id) {
        return BasvuruResponse.from(bul(id));
    }

    /**
     * Durum gunceller.
     *
     * <p>{@code OGRENCIYE_DONUSTU} ELLE atanamaz: o durum yalnizca gercekten ogrenci kaydi
     * olusturuldugunda ({@link #ogrenciyeDonustur}) olusur. Aksi halde ogrencisi olmayan
     * "donusturuldu" kayitlari olusur ve liste yalan soyler.
     */
    @Transactional
    public BasvuruResponse durumGuncelle(Long id, BasvuruDurumu durum) {
        if (durum == BasvuruDurumu.OGRENCIYE_DONUSTU) {
            throw new ConflictException(
                    "Bu durum elle atanamaz; başvuruyu \"Öğrenciye dönüştür\" ile işleyin.");
        }
        Basvuru b = bul(id);
        if (b.getDurum() == BasvuruDurumu.OGRENCIYE_DONUSTU) {
            throw new ConflictException("Öğrenciye dönüştürülmüş başvurunun durumu değiştirilemez.");
        }
        b.setDurum(durum);
        return BasvuruResponse.from(b);
    }

    /**
     * Basvuruyu ogrenci kaydina donusturur ve baglantiyi saklar.
     *
     * <p>Mukerrer donusum ENGELLENIR: ayni basvurudan ikinci kez ogrenci olusturmak, ayni
     * kisinin iki kaydi demektir (tahakkuk/yoklama boluner).
     */
    @Transactional
    public BasvuruResponse ogrenciyeDonustur(Long id, OgrenciyeDonusturRequest req) {
        Basvuru b = bul(id);
        if (b.getOgrenci() != null) {
            throw new ConflictException("Bu başvuru zaten öğrenciye dönüştürülmüş.");
        }

        // ⚠️ Ogrenci ELLE kurulmaz: StudentMapper kullanilir ki olusturma degismezleri
        // (ornegin baslangic statusu DENEME) TEK YERDE kalsin. Elle kurdugumuzda status
        // bos kaliyor ve NOT NULL kisitina takiliyordu.
        CreateStudentRequest istek = new CreateStudentRequest(
                b.getAd(),
                b.getSoyad(),
                req.tcKimlikNo(),
                req.dogumTarihi(),
                b.getTelefon(),
                req.yetiskinMi(),
                req.anneAd(),
                req.anneTcKimlikNo(),
                req.anneTelefon(),
                req.babaAd(),
                req.babaTcKimlikNo(),
                req.babaTelefon(),
                null,
                req.evAdresi(),
                // Basvurudaki e-posta veli adresi olarak tasinir: borc hatirlatma buradan gider.
                b.getEmail());
        Student kayitli = students.save(StudentMapper.toNewEntity(istek));

        b.setOgrenci(kayitli);
        b.setDurum(BasvuruDurumu.OGRENCIYE_DONUSTU);
        return BasvuruResponse.from(b);
    }

    /** ⚠️ findById DEGIL: PK-find tenant filtresine tabi degildir (capraz-tenant sizinti). */
    private Basvuru bul(Long id) {
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Başvuru bulunamadı: " + id));
    }
}
