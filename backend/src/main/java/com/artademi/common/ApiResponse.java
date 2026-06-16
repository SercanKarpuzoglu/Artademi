package com.artademi.common;

/**
 * Tum backend donuslerinin standart zarfi (bkz. api-contract skill).
 * Basarili: {@code {success:true, data, error:null, meta?}}
 * Hata:     {@code {success:false, data:null, error, meta:null}}
 *
 * @param <T> data tipi
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, PageMeta meta) {

    /** Basarili tekil yanit. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /** Basarili sayfali yanit (liste + sayfa bilgisi). */
    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    /** Hatali yanit. */
    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, null);
    }
}
