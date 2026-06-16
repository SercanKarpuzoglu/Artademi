package com.artademi.demo.dto;

import com.artademi.demo.DemoNote;

/**
 * Demo not yanit DTO'su. Entity disariya dogrudan donmez.
 */
public record DemoNoteResponse(Long id, String text) {

    public static DemoNoteResponse from(DemoNote note) {
        return new DemoNoteResponse(note.getId(), note.getText());
    }
}
