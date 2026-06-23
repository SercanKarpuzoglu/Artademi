package com.artademi.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.dto.TenantUserView;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Platform kullanici yonetimi ucu ({@code /api/platform/tenants/{id}/users}). TenantUserAdmin
 * (Keycloak) {@code @MockBean} ile taklit edilir → agsiz, deterministik. Yetki (yalniz SUPER_ADMIN),
 * tenant'in path'ten gelmesi ve validasyon dogrulanir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformUserControllerTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    TenantUserAdmin tenantUserAdmin;

    @Autowired
    MockMvc mockMvc;

    private static RequestPostProcessor superAdmin() {
        return jwt()
                .jwt(b -> b.claim("realm_access", Map.of("roles", List.of("SUPER_ADMIN"))))
                .authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }

    private static RequestPostProcessor token(String tenantId, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    @Test
    void superAdmin_listele() throws Exception {
        given(tenantUserAdmin.list(any(UUID.class))).willReturn(List.of(
                new TenantUserView("u1", "ayse", "Ayşe", "Yıldız", "ayse@x.com", null,
                        List.of("ADMIN"), true)));

        mockMvc.perform(get("/api/platform/tenants/{id}/users", TENANT).with(superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].kullaniciAdi").value("ayse"))
                .andExpect(jsonPath("$.data[0].roller[0]").value("ADMIN"));
    }

    @Test
    void superAdmin_ekle_201_tenantPathtenGecer() throws Exception {
        given(tenantUserAdmin.create(any(UUID.class), any())).willReturn(
                new TenantUserView("u2", "mehmet", "Mehmet", "Demir", "m@x.com", null,
                        List.of("FRONTDESK"), true));

        mockMvc.perform(post("/api/platform/tenants/{id}/users", TENANT).with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"mehmet\",\"ad\":\"Mehmet\",\"soyad\":\"Demir\","
                                + "\"email\":\"m@x.com\",\"roller\":[\"FRONTDESK\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kullaniciAdi").value("mehmet"));

        // tenant PATH'ten geçti (body'den değil)
        verify(tenantUserAdmin).create(eq(UUID.fromString(TENANT)), any());
    }

    @Test
    void superAdmin_ekle_gecersiz_400() throws Exception {
        // kullaniciAdi boş + roller boş -> 400 VALIDATION_ERROR (servise gitmeden)
        mockMvc.perform(post("/api/platform/tenants/{id}/users", TENANT).with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"\",\"ad\":\"A\",\"soyad\":\"B\",\"roller\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void superAdmin_sil_200() throws Exception {
        mockMvc.perform(delete("/api/platform/tenants/{id}/users/{uid}", TENANT, "u9").with(superAdmin()))
                .andExpect(status().isOk());
        verify(tenantUserAdmin).delete(UUID.fromString(TENANT), "u9");
    }

    @Test
    void normalAdmin_403_hepUclar() throws Exception {
        mockMvc.perform(get("/api/platform/tenants/{id}/users", TENANT).with(token(TENANT, "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/platform/tenants/{id}/users", TENANT).with(token(TENANT, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"x\",\"ad\":\"A\",\"soyad\":\"B\",\"roller\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/platform/tenants/{id}/users/{uid}", TENANT, "u1")
                        .with(token(TENANT, "ADMIN")))
                .andExpect(status().isForbidden());
    }
}
