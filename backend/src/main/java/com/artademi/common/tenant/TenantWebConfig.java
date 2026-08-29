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
    private final com.artademi.audit.TenantAuditInterceptor tenantAuditInterceptor;
    private final com.artademi.user.ParolaDegisikligiInterceptor parolaInterceptor;

    public TenantWebConfig(RequireTenantInterceptor requireTenantInterceptor,
            TenantStatusInterceptor tenantStatusInterceptor,
            com.artademi.audit.TenantAuditInterceptor tenantAuditInterceptor,
            com.artademi.user.ParolaDegisikligiInterceptor parolaInterceptor) {
        this.requireTenantInterceptor = requireTenantInterceptor;
        this.tenantStatusInterceptor = tenantStatusInterceptor;
        this.tenantAuditInterceptor = tenantAuditInterceptor;
        this.parolaInterceptor = parolaInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // /api/webhooks/** + /api/billing/callback: iyzico sunucudan/browser'dan JWT'siz gelir
        // (tenant claim yok); kimlik HMAC imza / tek-kullanimlik token ile dogrulanir.
        registry.addInterceptor(requireTenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/platform/**",
                        "/api/webhooks/**", "/api/billing/callback", "/api/public/**");
        // /api/billing/**: ASKIDA tenant'in admin'i ODEME YAPIP erisimini geri acabilmelidir
        // (subscription-billing skill: kesintide yalniz odeme akisi acik kalir).
        registry.addInterceptor(tenantStatusInterceptor)
                .addPathPatterns("/api/**")
                // /api/feedback: ASKIDA kurum destek talebi gonderebilmeli — erisimi kesilmis
                // kullanicinin bize ulasamamasi, sorunun cozumunu de imkansizlastirir.
                .excludePathPatterns("/api/ping", "/api/platform/**", "/api/me", "/api/me/**",
                        "/api/webhooks/**", "/api/billing/**", "/api/public/**", "/api/feedback");

        // ILK-PAROLA YAPTIRIMI (sunucu tarafi). Muaf uclar YALNIZCA kullanicinin bu durumdan
        // cikabilmesi icin gerekli olanlar: profil oku + parola degistir. Ayrica kimliksiz
        // uclar (ping/webhook/public/callback) ve platform konsolu — super.admin'in kilit
        // ekrani YOK, kilitlenirse cikis yolu kalmaz.
        registry.addInterceptor(parolaInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/me", "/api/me/change-password",
                        "/api/platform/**", "/api/webhooks/**", "/api/public/**",
                        "/api/billing/callback");

        // Kurum ici islem kaydi: her basarili degistirici istegi yazar. Platform/webhook/public
        // yollari HARIC — onlar kurum islemi degildir (platform izi V18'de ayrica tutulur).
        registry.addInterceptor(tenantAuditInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/ping", "/api/platform/**", "/api/webhooks/**",
                        "/api/public/**");
    }
}
