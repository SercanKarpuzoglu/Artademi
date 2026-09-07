package com.artademi.bildirim;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bildirim tercihleri ucu.
 *
 * <p>En onemli sozlesme: <b>varsayilan HEPSI KAPALI</b>. Kurum acikca acmadikca velilerine
 * otomatik mail gitmez — bu ozelligin guvenlik/itibar sinirdir, kazara acilmamalidir.
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@AutoConfigureMockMvc
@Testcontainers
class BildirimAyariEndpointTest {

    private static final String TENANT_A = "d1d1d1d1-1111-1111-1111-111111111111";
    private static final String TENANT_B = "d2d2d2d2-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

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

    private static String govde(boolean borc, boolean devamsizlik, boolean ozet, int gun) {
        return "{\"borcHatirlatmaOtomatik\":" + borc
                + ",\"devamsizlikBildirimi\":" + devamsizlik
                + ",\"haftalikOzet\":" + ozet
                + ",\"haftalikOzetGunu\":" + gun + "}";
    }

    @Test
    void varsayilan_HEPSI_KAPALI() throws Exception {
        // Kurum hic ayar kaydetmemisken hicbir otomatik gonderim acik OLMAMALI.
        String yeniTenant = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/bildirim-ayarlari").with(token(yeniTenant, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.borcHatirlatmaOtomatik").value(false))
                .andExpect(jsonPath("$.data.devamsizlikBildirimi").value(false))
                .andExpect(jsonPath("$.data.haftalikOzet").value(false));
    }

    @Test
    void guncelleme_kaydedilirVeGeriOkunur() throws Exception {
        mockMvc.perform(put("/api/bildirim-ayarlari")
                        .with(token(TENANT_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde(true, true, true, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.borcHatirlatmaOtomatik").value(true))
                .andExpect(jsonPath("$.data.haftalikOzetGunu").value(3));

        mockMvc.perform(get("/api/bildirim-ayarlari").with(token(TENANT_A, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devamsizlikBildirimi").value(true))
                .andExpect(jsonPath("$.data.haftalikOzetGunu").value(3));
    }

    @Test
    void ikinciGuncelleme_yeniSatirACMAZ_ayniSatiriGunceller() throws Exception {
        String t = UUID.randomUUID().toString();
        mockMvc.perform(put("/api/bildirim-ayarlari").with(token(t, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde(true, false, false, 1)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/bildirim-ayarlari").with(token(t, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde(false, true, false, 5)))
                .andExpect(status().isOk());

        // Kurum basina TEK satir; ikinci satir olussaydi hangisinin gecerli oldugu belirsiz kalirdi.
        mockMvc.perform(get("/api/bildirim-ayarlari").with(token(t, "ADMIN")))
                .andExpect(jsonPath("$.data.borcHatirlatmaOtomatik").value(false))
                .andExpect(jsonPath("$.data.devamsizlikBildirimi").value(true))
                .andExpect(jsonPath("$.data.haftalikOzetGunu").value(5));
    }

    @Test
    void baskaKurumunAyari_SIZMAZ() throws Exception {
        mockMvc.perform(put("/api/bildirim-ayarlari").with(token(TENANT_B, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde(true, true, true, 7)))
                .andExpect(status().isOk());

        // A, B'nin ayarini GORMEMELI (A hic kaydetmediyse varsayilan doner).
        String bagimsizTenant = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/bildirim-ayarlari").with(token(bagimsizTenant, "ADMIN")))
                .andExpect(jsonPath("$.data.borcHatirlatmaOtomatik").value(false))
                .andExpect(jsonPath("$.data.haftalikOzetGunu").value(1));
    }

    @Test
    void onBuro_ERISEMEZ_403() throws Exception {
        // Bu ayarlar velilere otomatik mail gonderilmesini belirler; on buro tek basina acamaz.
        mockMvc.perform(get("/api/bildirim-ayarlari").with(token(TENANT_A, "FRONTDESK")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gecersizGun_400() throws Exception {
        mockMvc.perform(put("/api/bildirim-ayarlari").with(token(TENANT_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde(false, false, true, 9)))
                .andExpect(status().isBadRequest());
    }
}
