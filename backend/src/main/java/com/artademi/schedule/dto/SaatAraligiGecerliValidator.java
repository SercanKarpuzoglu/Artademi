package com.artademi.schedule.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link SaatAraligiGecerli} kuralinin uygulanmasi. Saatlerden biri null ise burada karar verilmez
 * (alan duzeyi @NotNull zaten yakalar); ikisi de doluysa {@code bitisSaati > baslangicSaati}
 * olmalidir. Hata {@code bitisSaati} alanina baglanir (error.fields.bitisSaati).
 */
public class SaatAraligiGecerliValidator implements ConstraintValidator<SaatAraligiGecerli, SaatBilgisi> {

    @Override
    public boolean isValid(SaatBilgisi value, ConstraintValidatorContext context) {
        if (value == null || value.baslangicSaati() == null || value.bitisSaati() == null) {
            // Saatlerden biri null ise alan duzeyi @NotNull devreye girer; burada gecerli say.
            return true;
        }
        if (value.bitisSaati().isAfter(value.baslangicSaati())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Bitiş saati başlangıç saatinden sonra olmalıdır")
                .addPropertyNode("bitisSaati")
                .addConstraintViolation();
        return false;
    }
}
