package com.artademi.group.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sinif duzeyi kural: grup tipine gore ilgili salon/ucret alanlari zorunlu ve gecerli olmalidir.
 * <ul>
 *   <li>GRUP -> {@code salonId} zorunlu (hata {@code salonId} alanina) VE {@code aylikAidat}
 *       zorunlu ve &gt; 0 (hata {@code aylikAidat} alanina).</li>
 *   <li>OZEL -> {@code dersBasiUcret} zorunlu ve &gt; 0 (hata {@code dersBasiUcret} alanina);
 *       {@code salonId} opsiyonel.</li>
 * </ul>
 * Bean Validation uzerinden calistigi icin eksik/gecersizse GlobalExceptionHandler
 * 400 VALIDATION_ERROR (error.fields dolu) doner. tip null ise burada karar verilmez
 * (alan duzeyi @NotNull devreye girer).
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GrupTutarliValidator.class)
public @interface GrupTutarli {

    String message() default "Grup bilgisi tutarsız";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
