package com.artademi.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.common.tenant.TenantContext;
import com.artademi.finance.Accrual;
import com.artademi.finance.AccrualRepository;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.reminder.dto.BorcluAday;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import com.artademi.student.StudentStatus;
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
 * Borclu veli hatirlatmasi. En kritik davranislar: SOGUMA (ayni veliye ust uste mail gitmez —
 * itibar korumasi) ve mailsiz veliyi SESSIZCE atlamamak.
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@AutoConfigureMockMvc
@Testcontainers
class BorcHatirlatmaTest {

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
    BorcHatirlatmaService service;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    StudentRepository studentRepo;

    @Autowired
    AccrualRepository accrualRepo;

    private static long tcSayaci = 90_000_000_000L;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    private UUID tenant() {
        Tenant t = Tenant.create("Hatirlatma " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        return tenantRepo.save(t).getId();
    }

    /** Borclu ogrenci olusturur: tahakkuk var, odeme yok → bakiye pozitif. */
    private Long borcluOgrenci(UUID tenantId, String ad, String veliMail, String tutar) {
        TenantContext.set(tenantId);
        Student s = Student.create();
        s.setAd(ad);
        s.setSoyad("Test");
        s.setTcKimlikNo(String.valueOf(tcSayaci++));
        s.setDogumTarihi(LocalDate.of(2012, 3, 3));
        s.setStatus(StudentStatus.AKTIF);
        s.setYetiskinMi(false);
        s.setVeliMail(veliMail);
        Student kaydedilen = studentRepo.save(s);

        Accrual a = Accrual.create();
        a.setOgrenci(kaydedilen);
        a.setDonem("2026-08");
        a.setTutar(new BigDecimal(tutar));
        accrualRepo.save(a);
        return kaydedilen.getId();
    }

    private static RequestPostProcessor token(UUID tenantId, String rol) {
        return jwt().jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("preferred_username", "muhasebe")
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void borcluVeMailliOgrenci_gonderilebilirGorunur() {
        UUID t = tenant();
        Long id = borcluOgrenci(t, "Ada", "veli@ornek.com", "1500");

        TenantContext.set(t);
        List<BorcluAday> adaylar = service.adaylar();

        assertThat(adaylar).anySatisfy(a -> {
            assertThat(a.ogrenciId()).isEqualTo(id);
            assertThat(a.gonderilebilir()).isTrue();
            assertThat(a.bakiye()).isEqualByComparingTo("1500");
        });
    }

    @Test
    void veliMailiYok_SESSIZCE_atlanmaz_sebebiSoylenir() {
        UUID t = tenant();
        Long id = borcluOgrenci(t, "Mailsiz", null, "800");

        TenantContext.set(t);
        BorcluAday aday = service.adaylar().stream()
                .filter(a -> a.ogrenciId().equals(id)).findFirst().orElseThrow();

        assertThat(aday.gonderilebilir()).isFalse();
        assertThat(aday.engelSebebi()).contains("e-posta");
    }

    @Test
    void SOGUMA_ayniVeliyeUSTUSTE_mailGITMEZ() {
        UUID t = tenant();
        Long id = borcluOgrenci(t, "Tekrar", "veli2@ornek.com", "1000");

        TenantContext.set(t);
        var ilk = service.gonder(List.of(id));
        assertThat(ilk.gonderilen()).isEqualTo(1);
        verify(mailSender).send(any(SimpleMailMessage.class));

        // Hemen ardindan tekrar denendiginde GONDERILMEMELI — veli ust uste mail almamali,
        // aksi halde spam isaretler ve alan adimizin itibari zarar gorur.
        var ikinci = service.gonder(List.of(id));
        assertThat(ikinci.gonderilen()).isZero();
        assertThat(ikinci.satirlar().get(0).aciklama()).contains("zaten hatırlatıldı");
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void gunlukTavan_asilirsa_400() throws Exception {
        UUID t = tenant();
        List<Long> cokFazla = new java.util.ArrayList<>();
        for (long i = 1; i <= BorcHatirlatmaService.GUNLUK_TAVAN + 1; i++) {
            cokFazla.add(i);
        }
        String govde = "{\"ogrenciIdleri\":[" + String.join(",",
                cokFazla.stream().map(String::valueOf).toList()) + "]}";

        mockMvc.perform(post("/api/reminders").with(token(t, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(govde))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mailSender);
    }

    @Test
    void yetki_onBuroPARAYIgormez_hatirlatmaGONDEREMEZ() throws Exception {
        UUID t = tenant();

        mockMvc.perform(get("/api/reminders/candidates").with(token(t, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reminders/candidates").with(token(t, "FRONTDESK_ACCOUNTING")))
                .andExpect(status().isOk());
        // FRONTDESK parasal veri gormez (yetki matrisi) → borc hatirlatmasi da yapamaz.
        mockMvc.perform(get("/api/reminders/candidates").with(token(t, "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reminders/candidates").with(token(t, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantIzolasyonu_baskaKurumunBorclusuGORUNMEZ() {
        UUID a = tenant();
        UUID b = tenant();
        borcluOgrenci(a, "AKurumu", "a@ornek.com", "500");
        Long bId = borcluOgrenci(b, "BKurumu", "b@ornek.com", "700");

        TenantContext.set(a);
        assertThat(service.adaylar())
                .noneMatch(x -> x.ogrenciId().equals(bId));
    }
}
