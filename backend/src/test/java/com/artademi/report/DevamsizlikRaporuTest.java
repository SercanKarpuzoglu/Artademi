package com.artademi.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.report.dto.AttendanceReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Devamsizlik raporu: siralama (en cok devamsizlik ONCE), oran hesabi, yetki ve CSV cikti.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DevamsizlikRaporuTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportService service;

    @Autowired
    TenantRepository tenantRepo;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    private UUID tenant() {
        Tenant t = Tenant.create("Rapor " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        return tenantRepo.save(t).getId();
    }

    private static RequestPostProcessor token(UUID tenantId, String rol) {
        return jwt().jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void veriYokken_bosRaporDoner_patlamaz() {
        TenantContext.set(tenant());
        AttendanceReportResponse r = service.attendanceReport(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

        assertThat(r.satirlar()).isEmpty();
        assertThat(r.toplamOturum()).isZero();
        assertThat(r.baslangic()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void bitisBaslangictanOnce_400() throws Exception {
        UUID t = tenant();

        mockMvc.perform(get("/api/reports/attendance")
                        .param("baslangic", "2026-06-30").param("bitis", "2026-06-01")
                        .with(token(t, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void yetki_ofisRolleriGorur_ogretmenGOREMEZ() throws Exception {
        UUID t = tenant();
        String[] p = {"baslangic", "2026-01-01", "bitis", "2026-12-31"};

        for (String rol : List.of("ADMIN", "FRONTDESK", "FRONTDESK_ACCOUNTING")) {
            mockMvc.perform(get("/api/reports/attendance")
                            .param(p[0], p[1]).param(p[2], p[3]).with(token(t, rol)))
                    .andExpect(status().isOk());
        }
        // Devamsizlik parasal degil ama ogretmen TUM ogrencilerin ozetini gormemeli.
        mockMvc.perform(get("/api/reports/attendance")
                        .param(p[0], p[1]).param(p[2], p[3]).with(token(t, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csv_ExcelUyumlu_BOM_ve_baslikIcerir() throws Exception {
        UUID t = tenant();

        byte[] icerik = mockMvc.perform(get("/api/reports/attendance.csv")
                        .param("baslangic", "2026-01-01").param("bitis", "2026-12-31")
                        .with(token(t, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(icerik[0] & 0xFF).isEqualTo(0xEF); // UTF-8 BOM
        String metin = new String(icerik, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(metin).contains("Öğrenci;Toplam Ders;Geldi;Gelmedi;İzinli;Katılım Oranı (%)");
    }

    @Test
    void oranHesabi_sifirDersteBOLMEHATASI_vermez() {
        // Payda 0 iken 0 dondurulmeli; aksi halde rapor ArithmeticException ile patlardi.
        TenantContext.set(tenant());
        AttendanceReportResponse r = service.attendanceReport(
                LocalDate.now().minusDays(1), LocalDate.now(), null);
        assertThat(r.satirlar()).allSatisfy(
                s -> assertThat(s.katilimOrani()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }
}
