package com.artademi.enrollment;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.enrollment.dto.CreateEnrollmentRequest;
import com.artademi.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kayit (enrollment) ucu — ogrenci/grup yazma iliskisi. Ince controller: dogrular, servisi cagirir,
 * ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id} claim'inden
 * konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki: yazma (POST, leave) ve okuma (GET) ADMIN / FRONTDESK / FRONTDESK_ACCOUNTING (on buro
 * ogrenciyi gruba yazar). TEACHER hicbirine erisemez (403).
 *
 * <p>DELETE YOK: ayrilma PATCH /{id}/leave ile durum AYRILDI'ya alinir (veri korunur).
 *
 * <p>Yardimci uclar ({@code /api/groups/{id}/enrollments}, {@code /api/students/{id}/enrollments})
 * ayni arama Specification'ini grupId/ogrenciId sabitleyerek kullanir.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    /** Yeni kayit olustur (durum AKTIF), 201. */
    @PostMapping("/api/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnrollmentResponse> create(@Valid @RequestBody CreateEnrollmentRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek kayit (yoksa 404). */
    @GetMapping("/api/enrollments/{id}")
    public ApiResponse<EnrollmentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Ayrilma: durum AYRILDI, ayrilmaTarihi bugun (kayit silinmez). */
    @PatchMapping("/api/enrollments/{id}/leave")
    public ApiResponse<EnrollmentResponse> leave(@PathVariable Long id) {
        return ApiResponse.ok(service.leave(id));
    }

    /** Filtreli/sayfali liste: ?ogrenciId=&grupId=&durum=&page=0&size=20 (hepsi opsiyonel). */
    @GetMapping("/api/enrollments")
    public ApiResponse<List<EnrollmentResponse>> list(
            @RequestParam(required = false) Long ogrenciId,
            @RequestParam(required = false) Long grupId,
            @RequestParam(required = false) EnrollmentDurumu durum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return page(ogrenciId, grupId, durum, page, size);
    }

    /** Bir gruptaki kayitlar: ?durum= opsiyonel. */
    @GetMapping("/api/groups/{id}/enrollments")
    public ApiResponse<List<EnrollmentResponse>> byGroup(
            @PathVariable Long id,
            @RequestParam(required = false) EnrollmentDurumu durum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return page(null, id, durum, page, size);
    }

    /** Bir ogrencinin kayitlari: ?durum= opsiyonel. */
    @GetMapping("/api/students/{id}/enrollments")
    public ApiResponse<List<EnrollmentResponse>> byStudent(
            @PathVariable Long id,
            @RequestParam(required = false) EnrollmentDurumu durum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return page(id, null, durum, page, size);
    }

    private ApiResponse<List<EnrollmentResponse>> page(Long ogrenciId, Long grupId,
            EnrollmentDurumu durum, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<EnrollmentResponse> result = service.search(ogrenciId, grupId, durum, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }
}
