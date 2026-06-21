package com.artademi.common.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tenant interceptor'larini is uclarina baglar (SIRA ONEMLI — kayit sirasiyla calisirlar):
 * <ol>
 *   <li>{@link RequireTenantInterceptor} — tenant boşsa 400 TENANT_REQUIRED.</li>
 *   <li>{@link TenantStatusInterceptor} — tenant ASKIDA ise 403 TENANT_SUSPENDED.</li>
 * </ol>
 *
 * <p>RequireTenant muaf: {@code /api/ping}, {@code /api/platform/**} ({@code /actuator/**} zaten
 * {@code /api} altinda degil). TenantStatus ek olarak {@code /api/me/**} de muaftir: ASKIDA
 * kullanici kendi durumunu/profilini gorebilsin ("askidasiniz" ekrani /api/me ile calisir).
 */
@Configuration
public class TenantWebConfig implements WebMvcConfigurer {

    private final RequireTenantInterceptor requireTenantInterceptor;
    private final TenantStatusInterceptor tenantStatusInterceptor;

    public TenantWebConfig(RequireTenantInterceptor requireTenantInterceptor,
            TenantStatusInterceptor tenantStatusInterceptor) {
        this.requireTenantInterceptor = requireTenantInterceptor;
        this.tenantStatusInterceptor = tenantStatusInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(requireTenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/platform/**");
        registry.addInterceptor(tenantStatusInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/platform/**", "/api/me", "/api/me/**");
    }
}
