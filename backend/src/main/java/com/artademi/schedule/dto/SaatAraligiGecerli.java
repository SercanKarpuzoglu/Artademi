package com.artademi.schedule.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sinif duzeyi kural: {@code bitisSaati > baslangicSaati} olmalidir. Aksi halde hata
 * {@code bitisSaati} alanina baglanir (error.fields.bitisSaati). Bean Validation uzerinden
 * calistigi icin gecersizse GlobalExceptionHandler 400 VALIDATION_ERROR (error.fields dolu) doner.
 *
 * <p>Saatlerden biri null ise burada karar verilmez (alan duzeyi @NotNull devreye girer).
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SaatAraligiGecerliValidator.class)
public @interface SaatAraligiGecerli {

    String message() default "Saat aralığı geçersiz";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
