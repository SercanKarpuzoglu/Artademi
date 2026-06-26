package com.artademi.teacher.dto;

import com.artademi.teacher.HakedisTipi;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link HakedisTutarli} kuralinin LISTE duzeyinde uygulanmasi (Model C). Kurallar:
 * <ul>
 *   <li>En az 1 hakedis satiri olmalidir (yoksa {@code hakedisler} alanina hata).</li>
 *   <li>Her tip yalnizca 1 kez gecebilir (tekrarda {@code hakedisler} alanina hata).</li>
 *   <li>Her satirin tipiyle eslesen tutar alani zorunlu ve gecerli olmalidir:
 *     SAATLIK -> {@code saatlikUcret} &gt; 0; CIRO_ORANI -> 0 &lt; {@code ciroOrani} &le; 100;
 *     OZEL_DERS -> {@code dersBasiUcret} &gt; 0.</li>
 * </ul>
 * Hatalar ilgili alana baglanir (error.fields.hakedisler / hakedisler[i].saatlikUcret vb.) ve Bean
 * Validation uzerinden GlobalExceptionHandler 400 VALIDATION_ERROR doner.
 */
public class HakedisTutarliValidator implements ConstraintValidator<HakedisTutarli, HakedisBilgisi> {

    private static final BigDecimal YUZ = new BigDecimal("100");

    @Override
    public boolean isValid(HakedisBilgisi value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        List<HakedisSatiriRequest> rows = value.hakedisler();
        if (rows == null || rows.isEmpty()) {
            bindError(context, "hakedisler", "En az bir hakediş tipi tanımlanmalıdır");
            return false;
        }

        boolean valid = true;
        Set<HakedisTipi> gorulen = EnumSet.noneOf(HakedisTipi.class);
        for (int i = 0; i < rows.size(); i++) {
            HakedisSatiriRequest row = rows.get(i);
            if (row == null || row.tip() == null) {
                bindIndexedError(context, i, "tip", "Hakediş tipi zorunludur");
                valid = false;
                continue;
            }
            if (!gorulen.add(row.tip())) {
                bindError(context, "hakedisler", "Aynı hakediş tipi birden fazla kez girilemez");
                valid = false;
                continue;
            }
            valid &= validateRow(row, i, context);
        }
        return valid;
    }

    private static boolean validateRow(HakedisSatiriRequest row, int index,
            ConstraintValidatorContext context) {
        return switch (row.tip()) {
            case SAATLIK -> {
                if (row.saatlikUcret() != null && row.saatlikUcret().signum() > 0) {
                    yield true;
                }
                bindIndexedError(context, index, "saatlikUcret",
                        "Saatlik hakedişte saatlik ücret zorunlu ve 0'dan büyük olmalıdır");
                yield false;
            }
            case CIRO_ORANI -> {
                BigDecimal oran = row.ciroOrani();
                if (oran != null && oran.signum() > 0 && oran.compareTo(YUZ) <= 0) {
                    yield true;
                }
                bindIndexedError(context, index, "ciroOrani",
                        "Ciro oranı hakedişinde oran zorunlu ve 0 ile 100 arasında olmalıdır");
                yield false;
            }
            case OZEL_DERS -> {
                if (row.dersBasiUcret() != null && row.dersBasiUcret().signum() > 0) {
                    yield true;
                }
                bindIndexedError(context, index, "dersBasiUcret",
                        "Özel ders hakedişinde ders başı ücret zorunlu ve 0'dan büyük olmalıdır");
                yield false;
            }
        };
    }

    private static void bindError(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }

    private static void bindIndexedError(ConstraintValidatorContext context, int index, String field,
            String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("hakedisler")
                .addPropertyNode(field).inIterable().atIndex(index)
                .addConstraintViolation();
    }
}
