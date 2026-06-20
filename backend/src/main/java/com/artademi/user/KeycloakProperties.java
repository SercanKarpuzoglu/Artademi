package com.artademi.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak Admin REST entegrasyonu ayarlari (application.yml -> {@code artademi.keycloak.*}).
 *
 * <p>Tum degerler ortam degiskeninden ({@code KEYCLOAK_*}) gelir; {@code adminClientSecret}
 * icin varsayilan YOKTUR — sir koda gomulmez (bkz. spring-boot-backend guvenlik ilkeleri).
 *
 * @param baseUrl           Keycloak kok adresi, or. http://localhost:8080
 * @param realm             realm adi, or. Artademi
 * @param adminClientId     service account client (confidential), realm-management rolleriyle
 * @param adminClientSecret service account client secret (ortam degiskeninden)
 * @param appClientId       public client (Direct Access Grants) — mevcut parola dogrulamak icin
 */
@ConfigurationProperties(prefix = "artademi.keycloak")
public record KeycloakProperties(
        String baseUrl,
        String realm,
        String adminClientId,
        String adminClientSecret,
        String appClientId) {

    /** Admin REST taban yolu: {base}/admin/realms/{realm}. */
    public String adminBasePath() {
        return baseUrl + "/admin/realms/" + realm;
    }

    /** Token uc noktasi: {base}/realms/{realm}/protocol/openid-connect/token. */
    public String tokenEndpoint() {
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }
}
