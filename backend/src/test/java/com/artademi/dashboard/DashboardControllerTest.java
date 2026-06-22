package com.artademi.dashboard;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dashboard (Genel Bakis) entegrasyon testleri. ODAK: role gore alan filtresi — ozellikle
 * FRONTDESK cevabinda HICBIR parasal anahtar bulunmamasi (para sizintisi). Bos tenant'la calisir
 * (alan VARLIGI/YOKLUGU dogrulanir; deger 0/[] olabilir).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DashboardControllerTest {

    /** Bu testlere ozel, baska testlerle paylasilmayan izole tenant'lar. */
    private static final String T_ADMIN = "d0000001-0000-0000-0000-000000000001";
    private static final String T_ACC = "d0000002-0000-0000-0000-000000000002";
    private static final String T_FRONT = "d0000003-0000-0000-0000-000000000003";
    private static final String T_TEACHER = "d0000004-0000-0000-0000-000000000004";
    private static final String T_NONE = "d0000005-0000-0000-0000-000000000005";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

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
    void admin_tumFinansAlanlari() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(token(T_ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rol").value("ADMIN"))
                .andExpect(jsonPath("$.data.sayilar.aktifOgrenci").exists())
                .andExpect(jsonPath("$.data.sayilar.buAyTahsilat").exists())
                .andExpect(jsonPath("$.data.sayilar.buAyGider").exists())
                .andExpect(jsonPath("$.data.sayilar.buAyNet").exists())
                .andExpect(jsonPath("$.data.sayilar.bekleyenBorcToplam").exists())
                .andExpect(jsonPath("$.data.trend6Ay", hasSize(6)))
                .andExpect(jsonPath("$.data.trend6Ay[0].tahsilat").exists())
                .andExpect(jsonPath("$.data.trend6Ay[0].gider").exists())
                .andExpect(jsonPath("$.data.trend6Ay[0].net").exists());
    }

    @Test
    void accounting_tahsilatVar_giderNetYok() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(token(T_ACC, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rol").value("FRONTDESK_ACCOUNTING"))
                .andExpect(jsonPath("$.data.sayilar.buAyTahsilat").exists())
                .andExpect(jsonPath("$.data.sayilar.bekleyenBorcToplam").exists())
                // Gider/net admin'e ozel — accounting'te YOK.
                .andExpect(jsonPath("$.data.sayilar.buAyGider").doesNotExist())
                .andExpect(jsonPath("$.data.sayilar.buAyNet").doesNotExist())
                .andExpect(jsonPath("$.data.trend6Ay[0].tahsilat").exists())
                .andExpect(jsonPath("$.data.trend6Ay[0].gider").doesNotExist())
                .andExpect(jsonPath("$.data.trend6Ay[0].net").doesNotExist());
    }

    @Test
    void frontdesk_hicbirParasalAlanYok() throws Exception {
        ResultActions res = mockMvc.perform(get("/api/dashboard").with(token(T_FRONT, "FRONTDESK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rol").value("FRONTDESK"))
                .andExpect(jsonPath("$.data.sayilar.aktifOgrenci").exists())
                .andExpect(jsonPath("$.data.sayilar.aktifGrup").exists())
                // ⚠️ Parasal alanlar TIP-duzeyinde yok:
                .andExpect(jsonPath("$.data.sayilar.buAyTahsilat").doesNotExist())
                .andExpect(jsonPath("$.data.sayilar.bekleyenBorcToplam").doesNotExist())
                .andExpect(jsonPath("$.data.trend6Ay").doesNotExist())
                .andExpect(jsonPath("$.data.sonOdemeler").doesNotExist());

        // Ham JSON'da parasal anahtarlar literal olarak bulunmamali (sizinti kontrolu).
        String body = res.andReturn().getResponse().getContentAsString();
        for (String para : List.of("tahsilat", "gider", "bekleyenBorc", "trend6Ay", "sonOdemeler", "buAy")) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    body.contains(para), "FRONTDESK cevabinda parasal anahtar sizdi: " + para);
        }
    }

    @Test
    void teacher_sadeceKendi_paraYok() throws Exception {
        ResultActions res = mockMvc.perform(get("/api/dashboard").with(token(T_TEACHER, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rol").value("TEACHER"))
                .andExpect(jsonPath("$.data.kendiGruplar").exists())
                .andExpect(jsonPath("$.data.bugunDersler").exists())
                .andExpect(jsonPath("$.data.sonYoklamalar").exists())
                // Para yok:
                .andExpect(jsonPath("$.data.sayilar").doesNotExist())
                .andExpect(jsonPath("$.data.trend6Ay").doesNotExist());

        String body = res.andReturn().getResponse().getContentAsString();
        for (String para : List.of("tahsilat", "gider", "bekleyenBorc", "sonOdemeler", "buAy")) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    body.contains(para), "TEACHER cevabinda parasal anahtar sizdi: " + para);
        }
    }

    @Test
    void uygunRolYok_403() throws Exception {
        // Tenant var ama taninan is rolu yok -> 403 (super.admin tenant'siz oldugundan buraya gelmez).
        mockMvc.perform(get("/api/dashboard").with(token(T_NONE, "NOBODY")))
                .andExpect(status().isForbidden());
    }
}
