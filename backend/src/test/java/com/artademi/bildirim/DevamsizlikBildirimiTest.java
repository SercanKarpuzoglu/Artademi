package com.artademi.bildirim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Arrays;
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
 * Otomatik devamsizlik bildirimi.
 *
 * <p>En kritik davranis MUKERRER KALKANI: job her aksam calisir ve elle de tetiklenebilir;
 * ayni (ogrenci, oturum) icin veliye IKINCI kez mail GITMEMELIDIR. Iki kez uyarilan veli
 * sistemin guvenilirligini sorgular ve alan adimizi spam'e isaretleyebilir.
 */
@SpringBootTest(properties = "spring.mail.username=test@parsius.com")
@AutoConfigureMockMvc
@Testcontainers
class DevamsizlikBildirimiTest {

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
    ObjectMapper objectMapper;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    OtomatikBildirimService service;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    private static RequestPostProcessor admin(String tenantId) {
        List<GrantedAuthority> authorities = Arrays.stream(new String[] {"ADMIN"})
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return jwt()
                .jwt(b -> b.claim("tenant_id", tenantId)
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(authorities);
    }

    private long id(String body) throws Exception {
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long postId(String tenantId, String yol, String json) throws Exception {
        return id(mockMvc.perform(post(yol).with(admin(tenantId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** brans + ogretmen + salon + grup zinciri kurar, grup id'sini doner. */
    private long grupKur(String t, String ek) throws Exception {
        long brans = postId(t, "/api/branches", "{\"ad\":\"Brans-" + ek + "\"}");
        long ogretmen = postId(t, "/api/teachers", "{\"ad\":\"Hoca-" + ek + "\",\"soyad\":\"H\","
                + "\"hakedisler\":[{\"tip\":\"SAATLIK\",\"saatlikUcret\":200.00}],\"bransIds\":[]}");
        long salon = postId(t, "/api/rooms", "{\"ad\":\"Salon-" + ek + "\"}");
        return postId(t, "/api/groups", "{\"ad\":\"Grup-" + ek + "\",\"tip\":\"GRUP\",\"bransId\":"
                + brans + ",\"ogretmenId\":" + ogretmen + ",\"salonId\":" + salon
                + ",\"aylikAidat\":500.00}");
    }

    /** Veli e-postasi OLAN ogrenci — bildirim ancak adres varsa gonderilir. */
    private long ogrenciKur(String t, String ad, String tc, String veliMail) throws Exception {
        String mailJson = veliMail == null ? "" : ",\"veliMail\":\"" + veliMail + "\"";
        return postId(t, "/api/students", "{\"ad\":\"" + ad + "\",\"soyad\":\"Test\","
                + "\"tcKimlikNo\":\"" + tc + "\",\"dogumTarihi\":\"2010-01-01\","
                + "\"yetiskinMi\":true" + mailJson + "}");
    }

    private String yeniTenant() {
        Tenant t = Tenant.create("Bildirim " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        return tenantRepo.save(t).getId().toString();
    }

    /**
     * Oturum acilinca kayitli ogrenciler GELMEDI varsayilaniyla uretilir; yani oturumu acmak
     * "herkes gelmedi" demektir ve bildirim adaylari olusur.
     */
    private void oturumAc(String t, long grupId, LocalDate gun) throws Exception {
        mockMvc.perform(post("/api/attendance-sessions").with(admin(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grupId\":" + grupId + ",\"tarih\":\"" + gun + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void gelmeyenVeliyeBildirimGider_veIKINCI_CALISTIRMADA_TEKRARLANMAZ() throws Exception {
        String t = yeniTenant();
        LocalDate gun = LocalDate.of(2026, 4, 6);
        long grup = grupKur(t, "mukerrer");
        long ogrenci = ogrenciKur(t, "Ada", "51000000001", "veli@ornek.com");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        oturumAc(t, grup, gun);

        TenantContext.set(UUID.fromString(t));
        int ilk = service.devamsizlikBildirimleri(gun);
        int ikinci = service.devamsizlikBildirimleri(gun);
        TenantContext.clear();

        assertThat(ilk).isEqualTo(1);
        // ⚠️ Mukerrer kalkani: ikinci calistirma HICBIR mail gondermemeli.
        assertThat(ikinci).isZero();
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void veliMailiYoksa_SESSIZCE_atlanir_hataVERMEZ() throws Exception {
        String t = yeniTenant();
        LocalDate gun = LocalDate.of(2026, 4, 7);
        long grup = grupKur(t, "mailsiz");
        long ogrenci = ogrenciKur(t, "Mailsiz", "51000000002", null);
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        oturumAc(t, grup, gun);

        TenantContext.set(UUID.fromString(t));
        int gonderilen = service.devamsizlikBildirimleri(gun);
        TenantContext.clear();

        // Adres yoksa gonderemeyiz; is patlamamali, sadece atlamali.
        assertThat(gonderilen).isZero();
    }

    @Test
    void baskaGunun_devamsizligi_GONDERILMEZ() throws Exception {
        String t = yeniTenant();
        long grup = grupKur(t, "gun");
        long ogrenci = ogrenciKur(t, "Ada", "51000000003", "veli2@ornek.com");
        postId(t, "/api/enrollments", "{\"ogrenciId\":" + ogrenci + ",\"grupId\":" + grup + "}");
        oturumAc(t, grup, LocalDate.of(2026, 4, 8));

        TenantContext.set(UUID.fromString(t));
        int gonderilen = service.devamsizlikBildirimleri(LocalDate.of(2026, 4, 9));
        TenantContext.clear();

        assertThat(gonderilen).isZero();
    }

    @Test
    void yoklamaKaydiYok_gonderimYAPILMAZ() throws Exception {
        String t = yeniTenant();

        TenantContext.set(UUID.fromString(t));
        int gonderilen = service.devamsizlikBildirimleri(LocalDate.of(2026, 4, 10));
        TenantContext.clear();

        assertThat(gonderilen).isZero();
    }
}
