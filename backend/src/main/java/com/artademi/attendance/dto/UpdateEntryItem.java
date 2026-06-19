package com.artademi.attendance.dto;

import com.artademi.attendance.YoklamaDurumu;
import jakarta.validation.constraints.NotNull;

/**
 * Toplu yoklama guncellemesinde tek bir ogrencinin yeni durumu. PUT govdesi bunlardan olusan cıplak
 * bir dizidir ({@code [{ogrenciId, durum}, ...]}).
 *
 * <p>{@code ogrenciId} ve {@code durum} zorunlu. Listeyi {@code @Valid} ile aldigimizda eleman bazli
 * null kontrolu de yapilir; serviste ayrica savunmaci olarak {@link ValidationException} ile dogrulanir
 * (bos eleman -> 400). ogrenciId'nin oturumda bulunmamasi -> 404.
 */
public record UpdateEntryItem(
        @NotNull(message = "Öğrenci zorunludur")
        Long ogrenciId,

        @NotNull(message = "Durum zorunludur")
        YoklamaDurumu durum) {
}
