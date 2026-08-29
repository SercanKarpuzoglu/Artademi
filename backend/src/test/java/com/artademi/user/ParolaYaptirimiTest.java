package com.artademi.user;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * ILK-PAROLA YAPTIRIMI — kapatilan acigin regresyon testi.
 *
 * <p>Onceden bayrak yalnizca tarayicida kontrol ediliyordu; istegi dogrudan API'ye atan biri
 * parolasini hic degistirmeden her seye erisebiliyordu. Bu testler bypass'in KAPALI kaldigini
 * ve kullanicinin cikis yolunun ACIK kaldigini birlikte korur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ParolaYaptirimiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    KeycloakAdminClient kc;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    ParolaDegisikligiInterceptor interceptor;

    private static final String SUB = "kullanici-sub-1";
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant t = Tenant.create("Parola " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        tenantId = tenantRepo.save(t).getId();
        interceptor.temizle(SUB); // testler arasi onbellek sizmasin
    }

    /** Keycloak'tan donen kullanici temsili; bayrak parametreyle kurulur. */
    private void bayrak(boolean degistirmeliMi) {
        given(kc.getUserById(anyString())).willReturn(Map.of(
                "id", SUB,
                "username", "test.kullanici",
                "enabled", true,
                "attributes", Map.of(
                        "must_change_password", List.of(String.valueOf(degistirmeliMi)),
                        "tenant_id", List.of(tenantId.toString()))));
    }

    private RequestPostProcessor token() {
        return jwt().jwt(b -> b.subject(SUB)
                        .claim("tenant_id", tenantId.toString())
                        .claim("preferred_username", "test.kullanici")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void parolaDegistirilmemis_isUcuna_DOGRUDAN_erisim_403() throws Exception {
        bayrak(true);

        // Tarayiciyi atlayip API'ye dogrudan istek: ESKIDEN calisiyordu, artik kapali.
        mockMvc.perform(get("/api/students").with(token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/branches").with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"Bale\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void cikisYolu_ACIK_kalir() throws Exception {
        bayrak(true);

        // Kullanici kilitliyken bile profilini okuyabilmeli (kilit ekrani bunu kullanir),
        // aksi halde durumdan cikamaz.
        mockMvc.perform(get("/api/me").with(token()))
                .andExpect(status().isOk());
    }

    @Test
    void parolaDegistirilmis_erisim_ACIK() throws Exception {
        bayrak(false);

        mockMvc.perform(get("/api/students").with(token()))
                .andExpect(status().isOk());
    }

    @Test
    void keycloakUlasilamiyorsa_KULLANICI_KILITLENMEZ() throws Exception {
        // Fail-open bilincli karar: altyapi hatasi yuzunden calisan kurumu durdurmayiz.
        given(kc.getUserById(anyString())).willThrow(new IllegalStateException("Keycloak down"));

        mockMvc.perform(get("/api/students").with(token()))
                .andExpect(status().isOk());
    }
}
