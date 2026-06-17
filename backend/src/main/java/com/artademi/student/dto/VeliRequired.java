package com.artademi.student.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sinif duzeyi kural: ogrenci yetiskin DEGILSE en az bir veli (anne VEYA baba —
 * en azindan ad + TC) zorunludur. Bean Validation uzerinden calistigi icin eksikse
 * GlobalExceptionHandler 400 VALIDATION_ERROR (error.fields dolu) doner.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VeliRequiredValidator.class)
public @interface VeliRequired {

    String message() default "Yetişkin olmayan öğrenci için en az bir veli (anne veya baba — ad ve TC) zorunludur";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
