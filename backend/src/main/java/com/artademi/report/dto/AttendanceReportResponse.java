package com.artademi.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Devamsizlik raporu. Satirlar KATILIM ORANI ARTAN sirada gelir — yoneticinin gormek istedigi
 * once "en cok devamsizlik yapan" ogrencidir, alfabetik liste degil.
 */
public record AttendanceReportResponse(
        LocalDate baslangic,
        LocalDate bitis,
        long toplamOturum,
        List<AttendanceReportRow> satirlar) {
}
