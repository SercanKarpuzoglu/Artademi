package com.artademi.common.exception;

/**
 * Kaynak cakismasi (or. ayni kayit zaten var). GlobalExceptionHandler bunu
 * 409 / CONFLICT'e cevirir.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
