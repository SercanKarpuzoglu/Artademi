package com.artademi.common.tenant;

import com.artademi.common.exception.TenantRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Katman 2: tenant gerektiren is uclarinda, tenant baglami yoksa istegi daha
 * controller'a girmeden reddeder (sorgu hic calismaz). Hangi uclara uygulanacagi
 * {@link TenantWebConfig}'te belirlenir (/api/** haric /api/ping; actuator zaten
 * /api altinda degil).
 */
@Component
public class RequireTenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        if (TenantContext.get() == null) {
            throw new TenantRequiredException("Bu işlem için tenant bağlamı gerekli");
        }
        return true;
    }
}
