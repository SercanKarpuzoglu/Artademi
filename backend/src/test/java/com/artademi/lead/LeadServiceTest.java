package com.artademi.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.artademi.common.exception.ConflictException;
import com.artademi.lead.dto.LeadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * LeadService birim testleri: mail icerigi/basliklari, honeypot, IP soguma, SMTP yapilandirmasiz
 * ve gonderim hatasi davranislari. SMTP MOCK (ag yok).
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    JavaMailSender mailSender;

    LeadService service;

    static final LeadRequest TALEP = new LeadRequest("Demo iste", "Ada Yılmaz",
            "Lina Sanat", "ada@ornek.com", "0555 111 22 33", "");

    @BeforeEach
    void setUp() {
        service = new LeadService(mailSender, "info@artademi.com", "info@artademi.com",
                "sercan@parsius.com");
    }

    @Test
    void basariliTalep_infoyaMailGider_yanitlaTalepSahibine() {
        service.submit(TALEP, "1.2.3.4");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        SimpleMailMessage m = mail.getValue();
        assertThat(m.getTo()).containsExactly("info@artademi.com");
        assertThat(m.getReplyTo()).isEqualTo("ada@ornek.com");
        assertThat(m.getSubject()).isEqualTo("[Demo iste] Lina Sanat — Ada Yılmaz");
        assertThat(m.getText()).contains("Ada Yılmaz", "Lina Sanat", "ada@ornek.com",
                "0555 111 22 33");
    }

    @Test
    void honeypotDolu_mailGitmez_hataDaVerilmez() {
        LeadRequest bot = new LeadRequest("Demo iste", "Bot", "Bot AŞ", "bot@spam.com", null,
                "http://spam.example");

        service.submit(bot, "5.6.7.8");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void ayniIpArkaArkaya_409() {
        service.submit(TALEP, "9.9.9.9");

        assertThatThrownBy(() -> service.submit(TALEP, "9.9.9.9"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void farkliIpler_engellenmez() {
        service.submit(TALEP, "1.1.1.1");
        service.submit(TALEP, "2.2.2.2"); // exception yok
        verify(mailSender, org.mockito.Mockito.times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpYapilandirilmamis_409_ve_gonderimDenenmez() {
        service = new LeadService(mailSender, "info@artademi.com", "info@artademi.com", "");

        assertThatThrownBy(() -> service.submit(TALEP, "3.3.3.3"))
                .isInstanceOf(ConflictException.class);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpHatasi_409aCevrilir() {
        doThrow(new MailSendException("kota")).when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.submit(TALEP, "4.4.4.4"))
                .isInstanceOf(ConflictException.class);
    }
}
