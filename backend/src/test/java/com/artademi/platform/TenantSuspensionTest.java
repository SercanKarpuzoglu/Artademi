package com.artademi.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ASKIDA tenant login engeli (TenantStatusInterceptor):
 * ASKIDA tenant -> is ucu 403 TENANT_SUSPENDED; AKTIF tenant 200; tenant'siz SUPER_ADMIN ->
 * 400 TENANT_REQUIRED (status kontrolu atlanir, RequireTenant->Status sirasi korunur).
 * (/api/me muafiyeti gercek Keycloak ile canli curl'de dogrulanir — birim testte Keycloak yok.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenantSuspensionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepository;

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private static RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(b -> b.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private String newTenant(String ad, TenantStatus status) {
        Tenant t = Tenant.create(ad);
        t.setStatus(status);
        return tenantRepository.save(t).getId().toString();
    }

    @Test
    void askidaTenant_isUcu403() throws Exception {
        String askida = newTenant("Askida Kurum", TenantStatus.ASKIDA);
        mockMvc.perform(get("/api/students").with(token(askida, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_SUSPENDED"));
    }

    @Test
    void aktifTenant_isUcu200() throws Exception {
        String aktif = newTenant("Aktif Kurum", TenantStatus.AKTIF);
        mockMvc.perform(get("/api/students").with(token(aktif, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdmin_tenantsiz_400_suspendedDegil() throws Exception {
        mockMvc.perform(get("/api/students").with(superAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TENANT_REQUIRED"));
    }
}
