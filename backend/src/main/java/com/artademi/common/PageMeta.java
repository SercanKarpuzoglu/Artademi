package com.artademi.common;

import org.springframework.data.domain.Page;

/**
 * Sayfali liste yanitlarinin meta bilgisi (bkz. api-contract skill).
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    /** Spring Data {@link Page}'den uretir. */
    public static PageMeta of(Page<?> page) {
        return new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
