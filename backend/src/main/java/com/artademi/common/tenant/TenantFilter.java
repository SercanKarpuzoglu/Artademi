package com.artademi.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant kaynagini okuyan TEK yer. Kimligi dogrulanmis JWT'deki {@code tenant_id}
 * claim'ini okuyup {@link TenantContext}'e koyar; istek bitince temizler.
 *
 * <p>Guvenlik zincirinde {@code BearerTokenAuthenticationFilter}'dan SONRA calisir
 * (bkz. SecurityConfig), boylece authentication hazirdir ve claim okunabilir.
 *
 * <p><b>Fail-closed:</b> token yoksa / {@code tenant_id} claim'i yoksa / UUID parse
 * edilemezse tenant set EDILMEZ. Context bos kalir; {@link RequireTenantInterceptor}
 * is ucunu 400 TENANT_REQUIRED ile reddeder ve {@link TenantIdResolver} -1 ile bos
 * sonuc dondurur. Bu durumda hata firlatilmaz, sadece set edilmez.
 *
 * <p>{@code /api/ping} ve {@code /actuator/**} tenant gerektirmez.
 */
public class TenantFilter extends OncePerRequestFilter {

    static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            resolveTenantFromJwt();
            filterChain.doFilter(request, response);
        } finally {
            // Thread havuzda yeniden kullanilacagi icin baglam her zaman temizlenir.
            TenantContext.clear();
        }
    }

    private void resolveTenantFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return;
        }
        String raw = jwtAuth.getToken().getClaimAsString(TENANT_CLAIM);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            TenantContext.set(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            // Gecersiz/parse edilemeyen UUID -> tenant set edilmez (fail-closed).
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/ping".equals(path) || path.startsWith("/actuator");
    }
}
