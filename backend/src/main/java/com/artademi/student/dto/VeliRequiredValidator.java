package com.artademi.student.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link VeliRequired} kuralinin uygulanmasi: ogrenci yetiskin DEGILSE en az bir veli
 * (anne VEYA baba) icin hem ad hem TC dolu olmalidir. Eksikse hata mesaji {@code veli}
 * alanina baglanir (error.fields.veli).
 */
public class VeliRequiredValidator implements ConstraintValidator<VeliRequired, VeliBilgisi> {

    @Override
    public boolean isValid(VeliBilgisi value, ConstraintValidatorContext context) {
        if (value == null || value.yetiskinMi()) {
            return true;
        }
        boolean anneTam = notBlank(value.anneAd()) && notBlank(value.anneTcKimlikNo());
        boolean babaTam = notBlank(value.babaAd()) && notBlank(value.babaTcKimlikNo());
        if (anneTam || babaTam) {
            return true;
        }
        // Hatayi 'veli' alanina bagla (error.fields.veli dolacak).
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("veli")
                .addConstraintViolation();
        return false;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
