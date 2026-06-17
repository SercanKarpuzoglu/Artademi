package com.artademi.teacher.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * {@link HakedisTutarli} kuralinin uygulanmasi. hakedisTipi null ise burada karar verilmez
 * (alan duzeyi @NotNull zaten yakalar); doluysa ilgili tutar alani zorunlu/gecerli olmalidir.
 * Hata ilgili alana baglanir (error.fields.saatlikUcret / error.fields.ciroOrani).
 */
public class HakedisTutarliValidator implements ConstraintValidator<HakedisTutarli, HakedisBilgisi> {

    private static final BigDecimal YUZ = new BigDecimal("100");

    @Override
    public boolean isValid(HakedisBilgisi value, ConstraintValidatorContext context) {
        if (value == null || value.hakedisTipi() == null) {
            // hakedisTipi null ise alan duzeyi @NotNull devreye girer; burada gecerli say.
            return true;
        }
        return switch (value.hakedisTipi()) {
            case SAATLIK -> validateSaatlik(value.saatlikUcret(), context);
            case CIRO_ORANI -> validateCiro(value.ciroOrani(), context);
        };
    }

    private static boolean validateSaatlik(BigDecimal saatlikUcret, ConstraintValidatorContext context) {
        if (saatlikUcret != null && saatlikUcret.signum() > 0) {
            return true;
        }
        bindError(context, "saatlikUcret", "Saatlik hakedişte saatlik ücret zorunlu ve 0'dan büyük olmalıdır");
        return false;
    }

    private static boolean validateCiro(BigDecimal ciroOrani, ConstraintValidatorContext context) {
        if (ciroOrani != null && ciroOrani.signum() > 0 && ciroOrani.compareTo(YUZ) <= 0) {
            return true;
        }
        bindError(context, "ciroOrani", "Ciro oranı hakedişinde oran zorunlu ve 0 ile 100 arasında olmalıdır");
        return false;
    }

    private static void bindError(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
