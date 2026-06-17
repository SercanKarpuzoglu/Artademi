package com.artademi.student;

import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import com.artademi.student.dto.CreateStudentRequest;
import com.artademi.student.dto.StudentResponse;
import com.artademi.student.dto.UpdateStatusRequest;
import com.artademi.student.dto.UpdateStudentRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ogrenci Islemleri ucu. Ince controller: dogrular, servisi cagirir, ApiResponse doner.
 *
 * <p>Tenant ASLA parametre olarak ALINMAZ; TenantContext'e JWT'deki {@code tenant_id}
 * claim'inden konur (bkz. multi-tenancy / keycloak-auth).
 *
 * <p>Yetki: tum uclar ADMIN / FRONTDESK / FRONTDESK_ACCOUNTING. TEACHER erisemez (403).
 *
 * <p>DELETE YOK: silme yerine PATCH /{id}/status ile PASIF'e alinir (veri korunur).
 */
@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    /** Yeni ogrenci olustur (statu DENEME), 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /** Tek ogrenci (yoksa 404). */
    @GetMapping("/{id}")
    public ApiResponse<StudentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Ogrenci guncelle (statu degismez). */
    @PutMapping("/{id}")
    public ApiResponse<StudentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudentRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** Manuel statu degisikligi (PASIF'e alma dahil). */
    @PatchMapping("/{id}/status")
    public ApiResponse<StudentResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request.status()));
    }

    /** Filtreli/sayfali liste: ?status=AKTIF&q=ara&page=0&size=20 (status, q opsiyonel). */
    @GetMapping
    public ApiResponse<List<StudentResponse>> list(
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("soyad").ascending().and(Sort.by("ad").ascending()));
        Page<StudentResponse> result = service.search(status, q, pageable);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result));
    }

    /** Kardesler: ayni tenant icinde ayni anne VEYA baba TC'sine sahip diger ogrenciler. */
    @GetMapping("/{id}/siblings")
    public ApiResponse<List<StudentResponse>> siblings(@PathVariable Long id) {
        return ApiResponse.ok(service.siblings(id));
    }
}
