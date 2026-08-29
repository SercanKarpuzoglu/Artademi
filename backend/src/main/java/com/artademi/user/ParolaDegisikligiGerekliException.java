package com.artademi.user;

/**
 * Kullanici ilk parolasini henuz degistirmemis. 403 + {@code PASSWORD_CHANGE_REQUIRED} doner.
 */
public class ParolaDegisikligiGerekliException extends RuntimeException {

    public ParolaDegisikligiGerekliException(String message) {
        super(message);
    }
}
