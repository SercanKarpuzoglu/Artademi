package com.artademi.indirim;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.indirim.dto.IndirimRequest;
import com.artademi.indirim.dto.IndirimResponse;
import com.artademi.indirim.dto.OgrenciIndirimiRequest;
import com.artademi.indirim.dto.OgrenciIndirimiResponse;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indirim / kampanya (Dalga D). Tanim CRUD, ogrenciye atama ve tahakkuk aninda hesaplama.
 *
 * <p>HESAP KURALI ({@link #hesapla}): donemin ilk gununde gecerli atamalar bulunur; ORAN'lar toplanip brut
 * uzerinden bir kez uygulanir, TUTAR'lar toplanir; toplam indirim brut'u asamaz (net >= 0). Scale 2 HALF_UP.
 * Aciklama insan okunur: "Kardeş indirimi %15, Burs 500 ₺".
 */
@Service
public class IndirimService {

    private static final BigDecimal YUZ = new BigDecimal("100");

    private final IndirimTanimiRepository tanimlar;
    private final OgrenciIndirimiRepository atamalar;
    private final StudentRepository students;
    private final GroupRepository groups;

    public IndirimService(IndirimTanimiRepository tanimlar, OgrenciIndirimiRepository atamalar,
            StudentRepository students, GroupRepository groups) {
        this.tanimlar = tanimlar;
        this.atamalar = atamalar;
        this.students = students;
        this.groups = groups;
    }

    // ---------- tanimlar ----------

    @Transactional(readOnly = true)
    public List<IndirimResponse> list(Boolean aktif) {
        List<IndirimTanimi> liste = aktif == null ? tanimlar.findAllSirali() : tanimlar.findByAktif(aktif);
        return liste.stream().map(IndirimResponse::from).toList();
    }

    @Transactional
    public IndirimResponse create(IndirimRequest req) {
        dogrula(req);
        IndirimTanimi i = IndirimTanimi.create();
        uygula(i, req);
        return IndirimResponse.from(tanimlar.save(i));
    }

    @Transactional
    public IndirimResponse update(Long id, IndirimRequest req) {
        dogrula(req);
        IndirimTanimi i = tanimOrThrow(id);
        uygula(i, req);
        return IndirimResponse.from(i);
    }

    @Transactional
    public IndirimResponse durum(Long id, boolean aktif) {
        IndirimTanimi i = tanimOrThrow(id);
        i.setAktif(aktif);
        return IndirimResponse.from(i);
    }

    private static void dogrula(IndirimRequest req) {
        if (req.tip() == IndirimTipi.ORAN && req.deger().compareTo(YUZ) > 0) {
            throw new ValidationException("Oran %100'ü aşamaz");
        }
    }

    private static void uygula(IndirimTanimi i, IndirimRequest req) {
        i.setAd(req.ad().trim());
        i.setTip(req.tip());
        i.setDeger(req.deger().setScale(2, RoundingMode.HALF_UP));
        i.setAciklama(req.aciklama() == null || req.aciklama().isBlank() ? null : req.aciklama().trim());
    }

    // ---------- ogrenci atamalari ----------

    @Transactional(readOnly = true)
    public List<OgrenciIndirimiResponse> ogrenciIndirimleri(Long ogrenciId) {
        studentOrThrow(ogrenciId);
        return atamalar.findByOgrenci(ogrenciId).stream().map(OgrenciIndirimiResponse::from).toList();
    }

    @Transactional
    public OgrenciIndirimiResponse ata(Long ogrenciId, OgrenciIndirimiRequest req) {
        Student ogrenci = studentOrThrow(ogrenciId);
        IndirimTanimi tanim = tanimOrThrow(req.indirimId());
        if (!tanim.isAktif()) {
            throw new ValidationException("Pasif indirim atanamaz");
        }
        Group grup = req.grupId() == null ? null : groups.findScopedById(req.grupId())
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + req.grupId()));
        LocalDate baslangic = req.baslangic() == null ? LocalDate.now() : req.baslangic();
        if (req.bitis() != null && req.bitis().isBefore(baslangic)) {
            throw new ValidationException("Bitiş başlangıçtan önce olamaz");
        }
        OgrenciIndirimi o = OgrenciIndirimi.create();
        o.setOgrenci(ogrenci);
        o.setIndirim(tanim);
        o.setGrup(grup);
        o.setBaslangic(baslangic);
        o.setBitis(req.bitis());
        o.setAciklama(req.aciklama() == null || req.aciklama().isBlank() ? null : req.aciklama().trim());
        return OgrenciIndirimiResponse.from(atamalar.save(o));
    }

    /** Atamayi bitir: aktif=false, bitis yoksa bugun. Silinmez (gecmis tahakkuklarin gerekcesi kalsin). */
    @Transactional
    public OgrenciIndirimiResponse bitir(Long atamaId) {
        OgrenciIndirimi o = atamalar.findScopedById(atamaId)
                .orElseThrow(() -> new NotFoundException("İndirim ataması bulunamadı: " + atamaId));
        o.setAktif(false);
        if (o.getBitis() == null || o.getBitis().isAfter(LocalDate.now())) {
            o.setBitis(LocalDate.now());
        }
        return OgrenciIndirimiResponse.from(o);
    }

    // ---------- hesaplama ----------

    /** Brut uzerinden, verilen tarihte (donem basi) gecerli indirimleri uygular. Atama yoksa net = brut. */
    @Transactional(readOnly = true)
    public IndirimSonucu hesapla(Long ogrenciId, Long grupId, LocalDate tarih, BigDecimal brut) {
        BigDecimal brutS = brut.setScale(2, RoundingMode.HALF_UP);
        BigDecimal oranToplam = BigDecimal.ZERO;
        BigDecimal tutarToplam = BigDecimal.ZERO;
        List<String> parcalar = new ArrayList<>();
        for (OgrenciIndirimi o : atamalar.findByOgrenci(ogrenciId)) {
            if (!o.gecerli(tarih, grupId)) {
                continue;
            }
            IndirimTanimi t = o.getIndirim();
            if (t.getTip() == IndirimTipi.ORAN) {
                oranToplam = oranToplam.add(t.getDeger());
            } else {
                tutarToplam = tutarToplam.add(t.getDeger());
            }
            parcalar.add(t.getAd() + " " + t.etiket());
        }
        if (parcalar.isEmpty()) {
            return IndirimSonucu.yok(brutS);
        }
        BigDecimal indirim = brutS.multiply(oranToplam).divide(YUZ, 2, RoundingMode.HALF_UP).add(tutarToplam)
                .setScale(2, RoundingMode.HALF_UP);
        if (indirim.compareTo(brutS) > 0) {
            indirim = brutS;
        }
        return new IndirimSonucu(brutS, indirim, brutS.subtract(indirim), String.join(", ", parcalar));
    }

    private IndirimTanimi tanimOrThrow(Long id) {
        return tanimlar.findScopedById(id).orElseThrow(() -> new NotFoundException("İndirim bulunamadı: " + id));
    }

    private Student studentOrThrow(Long id) {
        return students.findScopedById(id).orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + id));
    }
}
