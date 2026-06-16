package com.artademi.common.exception;

/**
 * Aranan kayit bulunamadi. GlobalExceptionHandler bunu 404 / NOT_FOUND'a cevirir.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
