package com.artademi.group.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/**
 * {@link GrupTutarli} kuralinin uygulanmasi. tip null ise burada karar verilmez (alan duzeyi
 * @NotNull zaten yakalar); doluysa tipe gore ilgili alanlar zorunlu/gecerli olmalidir. Hatalar
 * ilgili alana baglanir (error.fields.salonId / aylikAidat / dersBasiUcret).
 */
public class GrupTutarliValidator implements ConstraintValidator<GrupTutarli, GrupBilgisi> {

    @Override
    public boolean isValid(GrupBilgisi value, ConstraintValidatorContext context) {
        if (value == null || value.tip() == null) {
            // tip null ise alan duzeyi @NotNull devreye girer; burada gecerli say.
            return true;
        }
        return switch (value.tip()) {
            case GRUP -> validateGrup(value, context);
            case OZEL -> validateOzel(value, context);
        };
    }

    /** GRUP: salonId zorunlu VE aylikAidat zorunlu ve > 0. */
    private static boolean validateGrup(GrupBilgisi value, ConstraintValidatorContext context) {
        boolean gecerli = true;
        if (value.salonId() == null) {
            bindError(context, "salonId", "Grup tipinde salon zorunludur");
            gecerli = false;
        }
        if (!pozitif(value.aylikAidat())) {
            bindError(context, "aylikAidat", "Grup tipinde aylık aidat zorunlu ve 0'dan büyük olmalıdır");
            gecerli = false;
        }
        return gecerli;
    }

    /** OZEL: dersBasiUcret zorunlu ve > 0; salonId opsiyonel. */
    private static boolean validateOzel(GrupBilgisi value, ConstraintValidatorContext context) {
        if (pozitif(value.dersBasiUcret())) {
            return true;
        }
        bindError(context, "dersBasiUcret", "Özel tipinde ders başı ücret zorunlu ve 0'dan büyük olmalıdır");
        return false;
    }

    private static boolean pozitif(BigDecimal tutar) {
        return tutar != null && tutar.signum() > 0;
    }

    private static void bindError(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
