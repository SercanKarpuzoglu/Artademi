package com.artademi.student;

import com.artademi.common.exception.ValidationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import com.artademi.common.exception.NotFoundException;
import com.artademi.student.dto.CreateStudentRequest;
import com.artademi.student.dto.StudentMapper;
import com.artademi.student.dto.StudentResponse;
import com.artademi.student.dto.UpdateStudentRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.artademi.common.exception.ConflictException;

/**
 * Ogrenci is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik
 * set edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Silme YOK: {@link #changeStatus} ile PASIF'e alinarak veri korunur.
 * Veli zorunlulugu validasyonu DTO uzerindeki {@code @VeliRequired} ile (400) saglanir.
 */
@Service
public class StudentService {

    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    /**
     * Yeni ogrenci olusturur; statu DENEME ile baslar.
     *
     * <p>KARA LISTE KALKANI: ayni TC ile kara listedeki bir kayit varsa ve {@code karaListeOnayi} true
     * DEGILSE 409 KARA_LISTE doner (sebep mesajda). TC benzersiz olmadigi icin bu kontrol olmadan kisi
     * yeni bir kayit acilarak kara listeyi atlayabilirdi. Engel degil UYARI: onaylanirsa kayit acilir.
     */
    @Transactional
    public StudentResponse create(CreateStudentRequest req) {
        karaListeKalkani(req.tcKimlikNo(), req.karaListeOnayi());
        Student saved = repository.save(StudentMapper.toNewEntity(req));
        return StudentResponse.from(saved);
    }

    /** Ayni TC kara listedeyse ve onay yoksa 409 KARA_LISTE. Public: basvuru donusturmesi de kullanir. */
    public void karaListeKalkani(String tcKimlikNo, Boolean onay) {
        if (tcKimlikNo == null || Boolean.TRUE.equals(onay)) {
            return;
        }
        List<Student> eskiler = repository.findKaraListedekilerByTc(tcKimlikNo);
        if (eskiler.isEmpty()) {
            return;
        }
        Student e = eskiler.get(0);
        throw new ConflictException("Bu TC daha önce kara listeye alınmış (" + e.getAd() + " " + e.getSoyad()
                + "): " + (e.getKaraListeAciklama() == null ? "sebep girilmemiş" : e.getKaraListeAciklama()),
                "KARA_LISTE");
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentResponse.from(findOrThrow(id));
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest req) {
        Student student = findOrThrow(id);
        StudentMapper.applyUpdate(student, req);
        return StudentResponse.from(student);
    }

    /** Manuel statu degisikligi (silme yerine PASIF'e alma dahil). */
    @Transactional
    public StudentResponse changeStatus(Long id, StudentStatus status) {
        Student student = findOrThrow(id);
        student.setStatus(status);
        return StudentResponse.from(student);
    }

    /**
     * Kara listeye al / cikar (Dalga B). Alirken aciklama ZORUNLU (gruba yazarken popup'ta gosterilir);
     * cikarirken sebep/tarih/ekleyen temizlenir. Ekleyen JWT preferred_username'den okunur.
     */
    @Transactional
    public StudentResponse karaListe(Long id, boolean karaListe, String aciklama) {
        Student student = findOrThrow(id);
        if (karaListe) {
            if (aciklama == null || aciklama.isBlank()) {
                throw new ValidationException("Kara liste açıklaması zorunludur");
            }
            student.setKaraListe(true);
            student.setKaraListeAciklama(aciklama.trim());
            student.setKaraListeTarihi(Instant.now());
            student.setKaraListeEkleyen(kullaniciAdi());
        } else {
            student.setKaraListe(false);
            student.setKaraListeAciklama(null);
            student.setKaraListeTarihi(null);
            student.setKaraListeEkleyen(null);
        }
        return StudentResponse.from(student);
    }

    private static String kullaniciAdi() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            if (u != null && !u.isBlank()) {
                return u;
            }
        }
        return "sistem";
    }

    /** Filtreli/sayfali liste; status ve q opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<StudentResponse> search(StudentStatus status, String q, Pageable pageable) {
        Specification<Student> spec = Specification
                .where(StudentSpecifications.hasStatus(status))
                .and(StudentSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(StudentResponse::from);
    }

    /**
     * Kardesler: ayni tenant icinde, kendisi haricinde, ayni anne VEYA baba TC'sine
     * sahip ogrenciler. Bos/null veli TC'leri eslesmemeli.
     */
    @Transactional(readOnly = true)
    public List<StudentResponse> siblings(Long id) {
        Student student = findOrThrow(id);
        String anneTc = blankToNull(student.getAnneTcKimlikNo());
        String babaTc = blankToNull(student.getBabaTcKimlikNo());
        if (anneTc == null && babaTc == null) {
            return List.of();
        }
        return repository.findSiblings(student.getId(), anneTc, babaTc).stream()
                .map(StudentResponse::from)
                .toList();
    }

    private Student findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Öğrenci bulunamadı: " + id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
