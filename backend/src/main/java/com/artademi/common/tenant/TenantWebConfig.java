package com.artademi.common.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link RequireTenantInterceptor}'u is uclarina baglar.
 *
 * <p>Uygulanir: {@code /api/**}. Muaf: {@code /api/ping} (saglik), {@code /actuator/**}
 * (zaten {@code /api} altinda degil) ve {@code /api/platform/**} — platform (SUPER_ADMIN)
 * uclari tenant-bagimsizdir; tenant_id'siz SUPER_ADMIN bunlara ulasabilmeli (diger /api/**
 * hala tenant ister, fail-closed korunur).
 */
@Configuration
public class TenantWebConfig implements WebMvcConfigurer {

    private final RequireTenantInterceptor requireTenantInterceptor;

    public TenantWebConfig(RequireTenantInterceptor requireTenantInterceptor) {
        this.requireTenantInterceptor = requireTenantInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(requireTenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/platform/**");
    }
}
