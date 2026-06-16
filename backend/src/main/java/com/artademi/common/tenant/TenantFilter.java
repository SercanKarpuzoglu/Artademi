package com.artademi.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant kaynagini okuyan TEK yer. Su an gecici olarak {@code X-Tenant-Id}
 * header'indan okur ve {@link TenantContext}'e koyar; istek bitince temizler.
 *
 * <p>2b-3'te bu sinifin govdesi JWT'deki {@code tenant_id} claim'ini okuyacak
 * sekilde degisecek; gerisi (TenantContext, TenantAware, aktivator) aynen kalir.
 *
 * <p>{@code /api/ping} ve {@code /actuator/**} tenant gerektirmez; header'i
 * olmayan istekler icin de tenant set edilmez (henuz auth yok).
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String raw = request.getHeader(TENANT_HEADER);
            if (raw != null && !raw.isBlank()) {
                try {
                    TenantContext.set(Long.valueOf(raw.trim()));
                } catch (NumberFormatException ignored) {
                    // Gecersiz header degeri -> tenant set edilmez (sorgular kisitlanmaz).
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // Thread havuzda yeniden kullanilacagi icin baglam her zaman temizlenir.
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/ping".equals(path) || path.startsWith("/actuator");
    }
}
