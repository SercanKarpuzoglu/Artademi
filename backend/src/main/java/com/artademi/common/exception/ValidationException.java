package com.artademi.common.exception;

/**
 * Is kurali dogrulama hatasi (or. uygun olmayan statudeki ogrenci gruba yazilamaz).
 * Bean Validation disindaki, servis katmaninda enforce edilen kosullar icindir.
 * GlobalExceptionHandler bunu 400 / VALIDATION_ERROR'a cevirir.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
