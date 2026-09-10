package com.artademi.donem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DonemRequest(
        @NotBlank(message = "Ad zorunludur") String ad,
        @NotNull(message = "Başlangıç zorunludur") LocalDate baslangic,
        @NotNull(message = "Bitiş zorunludur") LocalDate bitis) {
}
