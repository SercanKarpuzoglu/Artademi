package com.artademi.attendance;

import com.artademi.attendance.dto.CreateSessionRequest;
import com.artademi.attendance.dto.SessionResponse;
import com.artademi.attendance.dto.UpdateEntryItem;
import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.enrollment.Enrollment;
import com.artademi.enrollment.EnrollmentRepository;
import com.artademi.group.Group;
import com.artademi.group.GroupRepository;
import com.artademi.schedule.Schedule;
import com.artademi.schedule.ScheduleRepository;
import com.artademi.student.Student;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yoklama is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant filtresi aktif
 * oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir (bkz.
 * TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): gelen {@code grupId}/{@code programId}/
 * {@code ogrenciId}, ilgili OWNER repository'nin {@code findScopedById} ile cozulur; bulunamazsa
 * (baska tenant'a ait VEYA yok) {@link NotFoundException} (-> 404). Boylece baska tenant'in id'siyle
 * islem yapilamaz (sizinti yok).
 *
 * <p>TEACHER rolu yalnizca kendi gruplarina erisir; bu ince kural {@link AttendanceAccessGuard} ile
 * enforce edilir (create/get/bulk-update'te grup; liste'de ogretmen-id daraltmasi).
 *
 * <p>Is kurallari:
 * <ul>
 *   <li>Oturum olusturma: grup+programId tenant-guvenli cozulur; program grubuna ait olmali (400).
 *       Ayni grup+tarih icin oturum varsa 409. Gruptaki AKTIF kayitli ogrenciler icin GELMEDI
 *       varsayilani ile giris uretilir (aktif ogrenci yoksa girisler bos).</li>
 *   <li>Toplu guncelleme: her item icin oturumdaki ogrenci girisi bulunur (yoksa 404), durum
 *       set edilir.</li>
 * </ul>
 *
 * <p>Silme YOK.
 */
@Service
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceEntryRepository entryRepository;
    private final GroupRepository groupRepository;
    private final ScheduleRepository scheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceAccessGuard accessGuard;

    public AttendanceService(
            AttendanceSessionRepository sessionRepository,
            AttendanceEntryRepository entryRepository,
            GroupRepository groupRepository,
            ScheduleRepository scheduleRepository,
            EnrollmentRepository enrollmentRepository,
            AttendanceAccessGuard accessGuard) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.groupRepository = groupRepository;
        this.scheduleRepository = scheduleRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Yeni yoklama oturumu olusturur ve gruptaki AKTIF kayitli ogrenciler icin GELMEDI varsayilani
     * ile girisleri uretir.
     */
    @Transactional
    public SessionResponse create(CreateSessionRequest req) {
        Group grup = resolveGroup(req.grupId());
        accessGuard.assertCanAccessGroup(grup);

        Schedule program = null;
        if (req.programId() != null) {
            program = resolveSchedule(req.programId());
            if (program.getGrup() == null || !program.getGrup().getId().equals(grup.getId())) {
                throw new ValidationException("Program bu gruba ait değil");
            }
        }

        if (sessionRepository.existsByGrupAndTarih(grup.getId(), req.tarih())) {
            throw new ConflictException("Bu grup için bu tarihte zaten yoklama oturumu var");
        }

        AttendanceSession session = AttendanceSession.create();
        session.setGrup(grup);
        session.setTarih(req.tarih());
        session.setProgram(program);
        session.setNotu(req.notu());
        AttendanceSession saved = sessionRepository.save(session);

        List<Enrollment> aktifKayitlar = enrollmentRepository.findAktifByGrup(grup.getId());
        List<AttendanceEntry> entries = new ArrayList<>();
        for (Enrollment kayit : aktifKayitlar) {
            AttendanceEntry entry = AttendanceEntry.create();
            entry.setSession(saved);
            entry.setOgrenci(kayit.getOgrenci());
            entry.setDurum(YoklamaDurumu.GELMEDI);
            entries.add(entry);
        }
        List<AttendanceEntry> savedEntries = entries.isEmpty()
                ? List.of()
                : entryRepository.saveAll(entries);

        return SessionResponse.from(saved, savedEntries);
    }

    /** Tek oturum + girisleri (yoksa 404). TEACHER yalnizca kendi grubunun oturumunu gorebilir. */
    @Transactional(readOnly = true)
    public SessionResponse get(Long id) {
        AttendanceSession session = findSessionOrThrow(id);
        accessGuard.assertCanAccessGroup(session.getGrup());
        return SessionResponse.from(session, entryRepository.findBySessionId(session.getId()));
    }

    /**
     * Toplu durum guncellemesi. Her item icin oturumdaki ogrenci girisi bulunur (yoksa 404).
     * Bos/null item -> 400 (savunmaci). TEACHER yalnizca kendi grubunun oturumunu guncelleyebilir.
     */
    @Transactional
    public SessionResponse updateEntries(Long sessionId, List<UpdateEntryItem> items) {
        AttendanceSession session = findSessionOrThrow(sessionId);
        accessGuard.assertCanAccessGroup(session.getGrup());

        if (items != null) {
            for (UpdateEntryItem item : items) {
                if (item == null || item.ogrenciId() == null) {
                    throw new ValidationException("Öğrenci zorunludur");
                }
                if (item.durum() == null) {
                    throw new ValidationException("Durum zorunludur");
                }
                AttendanceEntry entry = entryRepository
                        .findBySessionIdAndOgrenciId(session.getId(), item.ogrenciId())
                        .orElseThrow(() -> new NotFoundException(
                                "Öğrenci bu oturumda bulunamadı: " + item.ogrenciId()));
                entry.setDurum(item.durum());
            }
        }

        return SessionResponse.from(session, entryRepository.findBySessionId(session.getId()));
    }

    /**
     * Filtreli/sayfali oturum listesi. TEACHER ise sorgu yalnizca kendi gruplarina daraltilir
     * (ogretmen-id spec). Tum filtreler opsiyonel.
     */
    @Transactional(readOnly = true)
    public Page<SessionResponse> search(Long grupId, LocalDate tarih, Pageable pageable) {
        Long ogretmenId = accessGuard.teacherScopeOgretmenId();
        Specification<AttendanceSession> spec = Specification
                .where(AttendanceSessionSpecifications.hasGrup(grupId))
                .and(AttendanceSessionSpecifications.hasTarih(tarih))
                .and(AttendanceSessionSpecifications.grupOgretmenId(ogretmenId));
        return sessionRepository.findAll(spec, pageable)
                .map(s -> SessionResponse.from(s, entryRepository.findBySessionId(s.getId())));
    }

    /**
     * Bir grubun [from, to] araligindaki oturumlari (tarihe gore artan sirali). grupId tenant-guvenli
     * cozulur (404). TEACHER yalnizca kendi grubuna erisebilir (403).
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> listByGroup(Long grupId, LocalDate from, LocalDate to) {
        Group grup = resolveGroup(grupId);
        accessGuard.assertCanAccessGroup(grup);
        // NOT: (:from IS NULL OR ...) JPQL anti-pattern'i Postgres'te untyped-null tip cikarim
        // hatasi verir; bu yuzden opsiyonel sinirlar Specification ile uygulanir (bkz. *Specifications).
        Specification<AttendanceSession> spec = Specification
                .where(AttendanceSessionSpecifications.hasGrup(grupId))
                .and(AttendanceSessionSpecifications.tarihGte(from))
                .and(AttendanceSessionSpecifications.tarihLte(to));
        return sessionRepository.findAll(spec, Sort.by("tarih").ascending()).stream()
                .map(s -> SessionResponse.from(s, entryRepository.findBySessionId(s.getId())))
                .toList();
    }

    private Group resolveGroup(Long grupId) {
        return groupRepository.findScopedById(grupId)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + grupId));
    }

    private Schedule resolveSchedule(Long programId) {
        return scheduleRepository.findScopedById(programId)
                .orElseThrow(() -> new NotFoundException("Program bulunamadı: " + programId));
    }

    private AttendanceSession findSessionOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return sessionRepository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Yoklama oturumu bulunamadı: " + id));
    }
}
