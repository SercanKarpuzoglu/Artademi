package com.artademi.feedback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
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
import org.springframework.mail.SimpleMailMessage;
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
 * Geri bildirim ucu: her rol gonderebilir, ASKIDA kurum da gonderebilir (destek yolu kapanmaz),
 * dogrulama calisir ve giris yapmamis istek reddedilir.
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@AutoConfigureMockMvc
@Testcontainers
class FeedbackEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TenantRepository tenantRepo;

    private Tenant tenant(TenantStatus st) {
        Tenant t = Tenant.create("Feedback " + UUID.randomUUID());
        t.setStatus(st);
        return tenantRepo.save(t);
    }

    private static RequestPostProcessor token(UUID tenantId, String rol, String sub) {
        return jwt().jwt(b -> b.subject(sub)
                        .claim("tenant_id", tenantId.toString())
                        .claim("preferred_username", "kullanici-" + rol.toLowerCase())
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static String govde(String tip, String mesaj) {
        return """
                {"tip":"%s","mesaj":"%s","eposta":"kullanici@ornek.com"}
                """.formatted(tip, mesaj);
    }

    @Test
    void ogretmenDeGonderebilir_rolKisitiYOK() throws Exception {
        Tenant t = tenant(TenantStatus.AKTIF);

        mockMvc.perform(post("/api/feedback")
                        .with(token(t.getId(), "TEACHER", UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("HATA", "Yoklama ekranında kayıt tuşu çalışmıyor.")))
                .andExpect(status().isOk());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void askidakiKurum_DA_gonderebilir() throws Exception {
        // Erisimi kesilen kurum destek talebi acabilmeli; aksi halde sorunu bildiremez.
        Tenant t = tenant(TenantStatus.ASKIDA);

        mockMvc.perform(post("/api/feedback")
                        .with(token(t.getId(), "ADMIN", UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("DESTEK", "Hesabımız askıya alındı, yardım eder misiniz?")))
                .andExpect(status().isOk());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void cokKisaMesaj_400() throws Exception {
        Tenant t = tenant(TenantStatus.AKTIF);

        mockMvc.perform(post("/api/feedback")
                        .with(token(t.getId(), "ADMIN", UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("ONERI", "kısa")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(mailSender);
    }

    @Test
    void ayniKullanici_cokSikGonderemez() throws Exception {
        Tenant t = tenant(TenantStatus.AKTIF);
        String sub = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/feedback")
                        .with(token(t.getId(), "ADMIN", sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("ONERI", "İlk mesajım yeterince uzun bir metindir.")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/feedback")
                        .with(token(t.getId(), "ADMIN", sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("ONERI", "Hemen ardından ikinci mesajı gönderiyorum.")))
                .andExpect(status().isConflict());

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void girisYapmamis_401() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde("HATA", "Giriş yapmadan gönderim denemesi.")))
                .andExpect(status().isUnauthorized());
    }
}
