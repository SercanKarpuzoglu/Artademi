package com.artademi.room;

import com.artademi.common.exception.NotFoundException;
import com.artademi.room.dto.CreateRoomRequest;
import com.artademi.room.dto.RoomMapper;
import com.artademi.room.dto.RoomResponse;
import com.artademi.room.dto.UpdateRoomRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salon is kurallari. {@code @Transactional} oldugundan cagrildiginda global tenant
 * filtresi aktif oturumda calisir; tenant_id yazma sirasinda TenantContext'ten otomatik
 * set edilir (bkz. TenantAware) — burada ELLE yonetilmez.
 *
 * <p>Silme YOK: {@link #changeActive} ile pasiflestirilerek veri korunur.
 */
@Service
public class RoomService {

    private final RoomRepository repository;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    /** Yeni salon olusturur; aktif true ile baslar. */
    @Transactional
    public RoomResponse create(CreateRoomRequest req) {
        Room saved = repository.save(RoomMapper.toNewEntity(req));
        return RoomResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {
        return RoomResponse.from(findOrThrow(id));
    }

    @Transactional
    public RoomResponse update(Long id, UpdateRoomRequest req) {
        Room room = findOrThrow(id);
        RoomMapper.applyUpdate(room, req);
        return RoomResponse.from(room);
    }

    /** Aktiflik degisikligi (pasiflestirme dahil; silme yerine). */
    @Transactional
    public RoomResponse changeActive(Long id, boolean aktif) {
        Room room = findOrThrow(id);
        room.setAktif(aktif);
        return RoomResponse.from(room);
    }

    /** Filtreli/sayfali liste; aktif ve q opsiyonel (null gecilebilir). */
    @Transactional(readOnly = true)
    public Page<RoomResponse> search(Boolean aktif, String q, Pageable pageable) {
        Specification<Room> spec = Specification
                .where(RoomSpecifications.hasAktif(aktif))
                .and(RoomSpecifications.matchesText(q));
        return repository.findAll(spec, pageable)
                .map(RoomResponse::from);
    }

    private Room findOrThrow(Long id) {
        // ONEMLI: findById (PK find) Hibernate tenant filtresine TABI DEGILDIR; baska
        // tenant'in kaydini sizdirir. Bu yuzden filtreli JPQL sorgusu kullanilir -> 404.
        return repository.findScopedById(id)
                .orElseThrow(() -> new NotFoundException("Salon bulunamadı: " + id));
    }
}
