package com.artademi.lead;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Lead ucu uc-uca: JWT'siz erisim (permitAll + tenant muafiyeti), validasyon, honeypot.
 * SMTP MOCK (ag yok).
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@AutoConfigureMockMvc
@Testcontainers
class LeadEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    JavaMailSender mailSender;

    @Autowired
    MockMvc mockMvc;

    @Test
    void jwtsizTalep_200_mailGider() throws Exception {
        mockMvc.perform(post("/api/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amac":"Satın al","ad":"Ada Yılmaz","kurum":"Lina Sanat",
                                 "email":"ada@ornek.com","telefon":"0555","website":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void eksikAlan_400ValidationError() throws Exception {
        mockMvc.perform(post("/api/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amac\":\"Satın al\",\"ad\":\"\",\"kurum\":\"X\",\"email\":\"gecersiz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void honeypotDolu_200AmaMailGitmez() throws Exception {
        mockMvc.perform(post("/api/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amac":"Demo iste","ad":"Bot","kurum":"Bot AŞ",
                                 "email":"bot@spam.com","website":"http://spam.example"}
                                """))
                .andExpect(status().isOk());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
