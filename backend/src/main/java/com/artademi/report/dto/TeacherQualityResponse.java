package com.artademi.report.dto;

import java.time.LocalDate;
import java.util.List;

/** Egitmen kalitesi raporu (Dalga F): tarih araligi + satirlar (katilim orani ARTAN — once dikkat gerektiren). */
public record TeacherQualityResponse(LocalDate baslangic, LocalDate bitis, List<TeacherQualityRow> satirlar) {
}
