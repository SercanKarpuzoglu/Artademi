package com.artademi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Kurum ici islem kaydi: otomatik yazim, okuma yetkisi ve TENANT IZOLASYONU.
 *
 * <p>En kritik test: bir kurumun yoneticisi baska kurumun islem kaydini GOREMEZ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenantAuditTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    TenantAuditRepository auditRepo;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    private Tenant tenant() {
        Tenant t = Tenant.create("Denetim " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        return tenantRepo.save(t);
    }

    private static RequestPostProcessor token(UUID tenantId, String rol) {
        return jwt().jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("preferred_username", "kullanici." + rol.toLowerCase())
                        .claim("name", "Test Kullanıcı")
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void degistiriciIstek_otomatikIzBirakir() throws Exception {
        Tenant t = tenant();

        mockMvc.perform(post("/api/branches")
                        .with(token(t.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Bale " + UUID.randomUUID() + "\"}"))
                .andExpect(status().is2xxSuccessful());

        TenantContext.set(t.getId());
        assertThat(auditRepo.findAll())
                .anySatisfy(a -> {
                    assertThat(a.getEylem()).isEqualTo("Branş eklendi");
                    assertThat(a.getActor()).isEqualTo("kullanici.admin");
                    assertThat(a.getActorAd()).isEqualTo("Test Kullanıcı");
                    assertThat(a.getMetot()).isEqualTo("POST");
                });
    }

    @Test
    void okumaIstegi_izBIRAKMAZ() throws Exception {
        Tenant t = tenant();
        TenantContext.set(t.getId());
        long once = auditRepo.count();
        TenantContext.clear();

        mockMvc.perform(get("/api/branches").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk());

        TenantContext.set(t.getId());
        // Listeleme her saniye yapilir; kaydi kirletmemeli.
        assertThat(auditRepo.count()).isEqualTo(once);
    }

    @Test
    void basarisizIstek_izBIRAKMAZ() throws Exception {
        Tenant t = tenant();
        TenantContext.set(t.getId());
        long once = auditRepo.count();
        TenantContext.clear();

        // Gecersiz govde → 400; denetim kaydi yazilmamali.
        mockMvc.perform(post("/api/branches")
                        .with(token(t.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"\"}"))
                .andExpect(status().isBadRequest());

        TenantContext.set(t.getId());
        assertThat(auditRepo.count()).isEqualTo(once);
    }

    @Test
    void tenantIzolasyonu_baskaKurumunKaydiGORUNMEZ() throws Exception {
        Tenant a = tenant();
        Tenant b = tenant();

        mockMvc.perform(post("/api/branches")
                        .with(token(a.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"A-Bale " + UUID.randomUUID() + "\"}"))
                .andExpect(status().is2xxSuccessful());

        // B'nin yoneticisi A'nin islemini GOREMEZ.
        mockMvc.perform(get("/api/audit").with(token(b.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // A kendi kaydini gorur.
        mockMvc.perform(get("/api/audit").with(token(a.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eylem").value("Branş eklendi"));
    }

    @Test
    void yalnizAdmin_okuyabilir() throws Exception {
        Tenant t = tenant();

        mockMvc.perform(get("/api/audit").with(token(t.getId(), "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").with(token(t.getId(), "TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void eylemMetni_metodaGoreUretilir() {
        assertThat(TenantAuditInterceptor.eylem("POST", "/api/students")).isEqualTo("Öğrenci eklendi");
        assertThat(TenantAuditInterceptor.eylem("PUT", "/api/groups/5")).isEqualTo("Grup güncellendi");
        assertThat(TenantAuditInterceptor.eylem("DELETE", "/api/users/abc")).isEqualTo("Kullanıcı silindi");
        assertThat(TenantAuditInterceptor.eylem("PATCH", "/api/students/3/status"))
                .isEqualTo("Öğrenci durumu değiştirildi");
    }

    @Test
    void kayitId_yoldanCikarilir() {
        assertThat(TenantAuditInterceptor.kayitId("/api/students/42")).isEqualTo("42");
        assertThat(TenantAuditInterceptor.kayitId("/api/students/42/status")).isEqualTo("42");
        assertThat(TenantAuditInterceptor.kayitId("/api/students")).isNull();
    }
}
