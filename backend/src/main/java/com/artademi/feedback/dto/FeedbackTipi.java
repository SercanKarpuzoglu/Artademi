package com.artademi.feedback.dto;

/** Geri bildirim tipi — mail konusunda gorunur, triyaji hizlandirir. */
public enum FeedbackTipi {

    HATA("Hata bildirimi"),
    ONERI("Öneri"),
    DESTEK("Destek talebi"),
    DIGER("Diğer");

    private final String etiket;

    FeedbackTipi(String etiket) {
        this.etiket = etiket;
    }

    public String etiket() {
        return etiket;
    }
}
