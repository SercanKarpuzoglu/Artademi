package com.artademi.student.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Kara listeye alma / cikarma (PATCH /api/students/{id}/kara-liste). {@code karaListe=true} ise
 * aciklama ZORUNLU (serviste dogrulanir: neden alindigi gruba yazarken popup'ta gosterilir).
 * {@code false} ise aciklama/tarih/ekleyen temizlenir.
 */
public record KaraListeRequest(
        @NotNull(message = "karaListe zorunludur")
        Boolean karaListe,
        String aciklama) {
}
