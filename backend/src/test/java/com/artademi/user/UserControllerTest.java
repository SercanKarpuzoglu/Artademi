package com.artademi.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Kullanici yonetimi (/api/users, /api/me) testleri. Keycloak gercek sunucuya erisemeyecegi icin
 * {@link KeycloakAdminClient} mock'lanir; servis/controller MANTIGI dogrulanir: tenant-eslesme ->
 * uyumsuzlukta 404 (sizinti yok), SUPER_ADMIN reddi -> 400, self-delete/self-deactivate -> 400,
 * create tenant'i context'ten alir, @PreAuthorize ADMIN-only (FRONTDESK -> 403), /api/me
 * mustChangePassword doner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN_SUB = "admin-sub-0000";
    private static final String TARGET_ID = "target-user-1234";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    KeycloakAdminClient kc;

    @Autowired
    MockMvc mockMvc;

    /** tenant_id + sub + roller iceren kimlik dogrulanmis JWT post-processor. */
    private static RequestPostProcessor token(String tenantId, String sub, String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(builder -> builder
                        .subject(sub)
                        .claim("preferred_username", username)
                        .claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(authorities);
    }

    private static RequestPostProcessor admin(String tenantId) {
        return token(tenantId, ADMIN_SUB, "admin", "ADMIN");
    }

    /** Verilen tenant'a ait UserRepresentation (id + tenant_id attribute + enabled). */
    private static Map<String, Object> userRep(String id, String tenant) {
        return Map.of(
                "id", id,
                "username", "ali",
                "firstName", "Ali",
                "lastName", "Veli",
                "email", "ali@example.com",
                "enabled", true,
                "attributes", Map.of(
                        "tenant_id", List.of(tenant),
                        "must_change_password", List.of("false")));
    }

    // ---------------------------------------------------------------------
    // @PreAuthorize
    // ---------------------------------------------------------------------

    @Test
    void frontdesk_usersList_403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(token(TENANT_A, "fd-sub", "fd", "FRONTDESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_usersList_200() throws Exception {
        when(kc.searchUsers(any(), any(), anyInt(), anyInt(), eq(TENANT_A)))
                .thenReturn(List.of());
        mockMvc.perform(get("/api/users").with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------------------------------------------------------------------
    // Tenant izolasyonu
    // ---------------------------------------------------------------------

    @Test
    void crossTenant_getViaUpdate_404_sizintiYok() throws Exception {
        // Hedef kullanici TENANT_B'ye ait; admin TENANT_A baglaminda.
        when(kc.getUserById(TARGET_ID)).thenReturn(userRep(TARGET_ID, TENANT_B));

        mockMvc.perform(patch("/api/users/{id}/active", TARGET_ID)
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        verify(kc, never()).setEnabled(anyString(), anyBoolean());
    }

    // ---------------------------------------------------------------------
    // Rol dogrulama
    // ---------------------------------------------------------------------

    @Test
    void create_superAdmin_400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"x\",\"ad\":\"X\",\"soyad\":\"Y\","
                                + "\"roller\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(kc, never()).createUser(any());
    }

    @Test
    void create_setsTenantFromContext() throws Exception {
        when(kc.createUser(any())).thenReturn(TARGET_ID);
        when(kc.getRealmRole("ADMIN")).thenReturn(Map.of("id", "role-admin", "name", "ADMIN"));
        when(kc.getUserById(TARGET_ID)).thenReturn(userRep(TARGET_ID, TENANT_A));
        when(kc.getUserRealmRoles(TARGET_ID)).thenReturn(List.of(Map.of("name", "ADMIN")));

        mockMvc.perform(post("/api/users")
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kullaniciAdi\":\"yeni\",\"ad\":\"Yeni\",\"soyad\":\"Kisi\","
                                + "\"email\":\"yeni@example.com\",\"roller\":[\"ADMIN\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(TARGET_ID));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, Object>> cap =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(kc).createUser(cap.capture());
        Map<String, Object> rep = cap.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) rep.get("attributes");
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(TENANT_A), attrs.get("tenant_id"));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("true"), attrs.get("must_change_password"));
        verify(kc).resetPassword(eq(TARGET_ID), eq("Artademi2026!"), eq(false));
    }

    // ---------------------------------------------------------------------
    // Self-guard
    // ---------------------------------------------------------------------

    @Test
    void selfDeactivate_400() throws Exception {
        when(kc.getUserById(ADMIN_SUB)).thenReturn(userRep(ADMIN_SUB, TENANT_A));

        mockMvc.perform(patch("/api/users/{id}/active", ADMIN_SUB)
                        .with(admin(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktif\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(kc, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void selfDelete_400() throws Exception {
        when(kc.getUserById(ADMIN_SUB)).thenReturn(userRep(ADMIN_SUB, TENANT_A));

        mockMvc.perform(delete("/api/users/{id}", ADMIN_SUB).with(admin(TENANT_A)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(kc, never()).deleteUser(anyString());
    }

    @Test
    void delete_crossTenant_404() throws Exception {
        when(kc.getUserById(TARGET_ID)).thenReturn(userRep(TARGET_ID, TENANT_B));

        mockMvc.perform(delete("/api/users/{id}", TARGET_ID).with(admin(TENANT_A)))
                .andExpect(status().isNotFound());

        verify(kc, never()).deleteUser(anyString());
    }

    // ---------------------------------------------------------------------
    // /api/me
    // ---------------------------------------------------------------------

    @Test
    void me_returnsMustChangePassword() throws Exception {
        Map<String, Object> rep = Map.of(
                "id", ADMIN_SUB,
                "username", "admin",
                "firstName", "Ad",
                "lastName", "Min",
                "email", "admin@example.com",
                "attributes", Map.of(
                        "tenant_id", List.of(TENANT_A),
                        "telefon", List.of("05001112233"),
                        "must_change_password", List.of("true")));
        when(kc.getUserById(ADMIN_SUB)).thenReturn(rep);

        mockMvc.perform(get("/api/me").with(admin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.telefon").value("05001112233"))
                .andExpect(jsonPath("$.data.kullaniciAdi").value("admin"));
    }

    @Test
    void changePassword_wrongCurrent_400() throws Exception {
        when(kc.verifyPassword(eq("admin"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/me/change-password")
                        .with(token(TENANT_A, ADMIN_SUB, "admin", "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mevcutParola\":\"yanlis\",\"yeniParola\":\"uzunYeni123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(kc, never()).resetPassword(anyString(), anyString(), anyBoolean());
    }
}
