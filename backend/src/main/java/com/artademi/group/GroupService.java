package com.artademi.group;

import com.artademi.branch.Branch;
import com.artademi.branch.BranchRepository;
import com.artademi.common.exception.NotFoundException;
import com.artademi.group.dto.CreateGroupRequest;
import com.artademi.group.dto.GroupMapper;
import com.artademi.group.dto.GroupResponse;
import com.artademi.group.dto.UpdateGroupRequest;
import com.artademi.room.Room;
import com.artademi.room.RoomRepository;
import com.artademi.teacher.Teacher;
import com.artademi.teacher.TeacherRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grup is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant filtresi aktif
 * oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik set edilir (bkz.
 * TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Capraz-tenant referans dogrulamasi (KRITIK): create/update'te gelen bransId, ogretmenId
 * (her zaman) ve salonId (doluysa) ilgili repository'nin {@code findScopedById} metodu ile cozulur;
 * bulunamazsa (baska tenant'a ait VEYA yok) {@link NotFoundException} (-> 404). Boylece baska
 * tenant'in brans/ogretmen/salon id'siyle grup olusturulamaz/guncellenemez (sizinti yok).
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur.
 */
@Service
public class GroupService {

    private final GroupRepository repository;
    private final BranchRepository branchRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;

    public GroupService(GroupRepository repository, BranchRepository branchRepository,
            TeacherRepository teacherRepository, RoomRepository roomRepository) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
    }

    /** Yeni grup olusturur; aktif true ile baslar. */
    @Transactional
    public GroupResponse create(CreateGroupRequest req) {
        Branch brans = resolveBranch(req.bransId());
        Teacher ogretmen = resolveTeacher(req.ogretmenId());
        Room salon = resolveRoom(req.salonId());
        Group saved = repository.save(GroupMapper.toNewEntity(req, brans, ogretmen, salon));
        return GroupResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long id) {
        return GroupResponse.from(findOrThrow(id));
    }

    @Transactional
    public GroupResponse update(Long id, UpdateGroupRequest req) {
        Group group = findOrThrow(id);
        Branch brans = resolveBranch(req.bransId());
        Teacher ogretmen = resolveTeacher(req.ogretmenId());
        Room salon = resolveRoom(req.salonId());
        GroupMapper.applyUpdate(group, req, brans, ogretmen, salon);
        return GroupResponse.from(group);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public GroupResponse changeActive(Long id, boolean aktif) {
        Group group = findOrThrow(id);
        group.setAktif(aktif);
        return GroupResponse.from(group);
    }

    /** Filtreli/sayfali liste; tum filtreler opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<GroupResponse> search(GrupTipi tip, Boolean aktif, Long bransId, Long ogretmenId,
            Long salonId, String q, Pageable pageable) {
        Specification<Group> spec = Specification
                .where(GroupSpecifications.hasTip(tip))
                .and(GroupSpecifications.hasAktif(aktif))
                .and(GroupSpecifications.hasBrans(bransId))
                .and(GroupSpecifications.hasOgretmen(ogretmenId))
                .and(GroupSpecifications.hasSalon(salonId))
                .and(GroupSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(GroupResponse::from);
    }

    /**
     * bransId'yi tenant-guvenli ({@code findScopedById}) cozer. Bulunamazsa (baska tenant'a ait
     * veya yok) -> 404, sizinti yok.
     */
    private Branch resolveBranch(Long bransId) {
        return branchRepository.findScopedById(bransId)
                .orElseThrow(() -> new NotFoundException("Branş bulunamadı: " + bransId));
    }

    private Teacher resolveTeacher(Long ogretmenId) {
        return teacherRepository.findScopedById(ogretmenId)
                .orElseThrow(() -> new NotFoundException("Öğretmen bulunamadı: " + ogretmenId));
    }

    /** salonId doluysa tenant-guvenli cozer (yoksa 404); null ise salon yok (OZEL'de gecerli). */
    private Room resolveRoom(Long salonId) {
        if (salonId == null) {
            return null;
        }
        return roomRepository.findScopedById(salonId)
                .orElseThrow(() -> new NotFoundException("Salon bulunamadı: " + salonId));
    }

    private Group findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska tenant'in
        // kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Grup bulunamadı: " + id));
    }
}
