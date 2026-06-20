package com.artademi.user;

import java.util.Collection;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Oturum sahibinin JWT'sinden kimlik bilgilerini okuyan kucuk yardimci (keycloak-auth).
 *
 * <p>{@code sub} self-koruma (kendini silme/pasife alma) ve /api/me icin; {@code realm_access.roles}
 * /api/me yaniti icin kullanilir. {@link AttendanceAccessGuard} ile ayni okuma desenini izler.
 */
@Component
public class CurrentUser {

    /** Mevcut JWT (yoksa 403). */
    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new AccessDeniedException("Kimlik doğrulanamadı");
    }

    /** Keycloak kullanici id'si (JWT sub). */
    public String sub() {
        return jwt().getSubject();
    }

    /** preferred_username (kullanici adi). */
    public String username() {
        return jwt().getClaimAsString("preferred_username");
    }

    /** realm_access.roles claim'inden rol adlari (ROLE_ oneksiz). */
    @SuppressWarnings("unchecked")
    public List<String> realmRoles() {
        Object realmAccess = jwt().getClaim("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object roles = ((java.util.Map<String, Object>) map).get("roles");
            if (roles instanceof Collection<?> col) {
                return col.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    /** Verilen ROLE_ authority'sine sahip mi. */
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
