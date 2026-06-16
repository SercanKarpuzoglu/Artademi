package com.artademi.common;

import java.util.Map;

/**
 * Hata govdesi (bkz. api-contract skill).
 *
 * @param code    makine-okur sabit: NOT_FOUND, VALIDATION_ERROR, CONFLICT, INTERNAL, ...
 * @param message kullaniciya gosterilebilir Turkce mesaj
 * @param fields  alan bazli dogrulama hatalari (yoksa null)
 */
public record ApiError(String code, String message, Map<String, String> fields) {

    /** Alan hatasi olmayan basit hata. */
    public ApiError(String code, String message) {
        this(code, message, null);
    }
}
