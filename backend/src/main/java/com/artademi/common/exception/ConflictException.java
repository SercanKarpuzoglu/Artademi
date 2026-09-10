package com.artademi.common.exception;

/**
 * 409 Conflict. Varsayilan kod "CONFLICT"; istemcinin ayirt etmesi gereken ozel durumlar
 * (ornegin kara liste uyarisi) farkli bir kodla firlatilir — mesaj metnine bagimlilik yok.
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String message) {
        this(message, "CONFLICT");
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
