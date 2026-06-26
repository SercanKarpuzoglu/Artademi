package com.artademi.teacher.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sinif duzeyi kural (Model C — hakedis LISTESI): en az 1 satir, her tip &le;1 kez ve her satirin
 * tipiyle eslesen tutar alani zorunlu/gecerli olmalidir.
 * <ul>
 *   <li>SAATLIK -> {@code saatlikUcret} zorunlu ve &gt; 0.</li>
 *   <li>CIRO_ORANI -> {@code ciroOrani} zorunlu ve 0 &lt; oran &le; 100.</li>
 *   <li>OZEL_DERS -> {@code dersBasiUcret} zorunlu ve &gt; 0.</li>
 * </ul>
 * Bean Validation uzerinden calistigi icin eksik/gecersizse GlobalExceptionHandler
 * 400 VALIDATION_ERROR (error.fields dolu) doner.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HakedisTutarliValidator.class)
public @interface HakedisTutarli {

    String message() default "Hakediş bilgisi tutarsız";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
