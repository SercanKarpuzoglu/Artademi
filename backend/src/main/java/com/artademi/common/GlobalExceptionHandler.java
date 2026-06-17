package com.artademi.common;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.TenantRequiredException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tek merkezi hata yonetimi. Tum hatalar {@link ApiResponse#fail} zarfiyla,
 * api-contract'taki sabit {@code code}'larla doner. Ham stacktrace SIZDIRILMAZ.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 — kayit bulunamadi. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(new ApiError("NOT_FOUND", ex.getMessage())));
    }

    /** 409 — kaynak cakismasi. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(new ApiError("CONFLICT", ex.getMessage())));
    }

    /**
     * 403 — rol/yetki reddi. Spring Security 6.3'te method-security (@PreAuthorize)
     * reddi {@link AuthorizationDeniedException} (AccessDeniedException alt sinifi)
     * firlatir; genel Exception handler'ina dusup 500 olmamali.
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(new ApiError("FORBIDDEN", "Bu işlem için yetkiniz yok")));
    }

    /** 400 — tenant gerektiren is ucuna tenant baglami olmadan erisim; sorgu calismaz. */
    @ExceptionHandler(TenantRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantRequired(TenantRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ApiError("TENANT_REQUIRED", ex.getMessage())));
    }

    /** 400 — Bean Validation hatalari; alan bazli mesajlar error.fields'a doldurulur. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            // Ayni alan icin ilk hata mesajini koru.
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ApiError("VALIDATION_ERROR", "Doğrulama hatası", fields)));
    }

    /** 500 — beklenmeyen hata. Detay loglanir, kullaniciya genel mesaj doner. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Beklenmeyen hata", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(new ApiError("INTERNAL", "Beklenmeyen bir hata oluştu")));
    }
}
