package com.artademi.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.artademi.audit.TenantAuditRepository;
import com.artademi.common.tenant.TenantContext;
import com.artademi.platform.Tenant;
import com.artademi.platform.TenantRepository;
import com.artademi.platform.TenantStatus;
import com.artademi.student.Student;
import com.artademi.student.StudentRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
 * Veri disa aktarma: yetki, icerik, Excel uyumu ve en onemlisi TENANT IZOLASYONU —
 * bir kurumun disa aktardigi dosyada baska kurumun ogrencisi ASLA bulunmamali.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VeriDisaAktarmaTest {

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
    StudentRepository studentRepo;

    @Autowired
    TenantAuditRepository auditRepo;

    @AfterEach
    void temizle() {
        TenantContext.clear();
    }

    /** Her ogrenciye essiz TC (kolon NOT NULL ve tekil). */
    private static long tcSayaci = 1;

    private Tenant tenant() {
        Tenant t = Tenant.create("Export " + UUID.randomUUID());
        t.setStatus(TenantStatus.AKTIF);
        return tenantRepo.save(t);
    }

    private void ogrenciEkle(UUID tenantId, String ad, String soyad) {
        TenantContext.set(tenantId);
        Student s = Student.create();
        s.setAd(ad);
        s.setSoyad(soyad);
        s.setTcKimlikNo(String.valueOf(10_000_000_000L + tcSayaci++)); // NOT NULL kolon
        s.setDogumTarihi(java.time.LocalDate.of(2010, 5, 1));          // NOT NULL kolon
        s.setStatus(com.artademi.student.StudentStatus.AKTIF);
        s.setYetiskinMi(true);
        studentRepo.save(s);
        TenantContext.clear();
    }

    private static RequestPostProcessor token(UUID tenantId, String rol) {
        return jwt().jwt(b -> b.claim("tenant_id", tenantId.toString())
                        .claim("preferred_username", "yonetici")
                        .claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(List.of((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    /** ZIP icindeki dosya adlarini ve tum metin icerigini dondurur. */
    private static Icerik zipOku(byte[] zip) throws Exception {
        List<String> dosyalar = new ArrayList<>();
        StringBuilder metin = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                dosyalar.add(e.getName());
                metin.append(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return new Icerik(dosyalar, metin.toString());
    }

    private record Icerik(List<String> dosyalar, String metin) {
    }

    @Test
    void tenantIzolasyonu_BASKA_kurumunOgrencisi_DOSYAYA_GIRMEZ() throws Exception {
        Tenant a = tenant();
        Tenant b = tenant();
        ogrenciEkle(a.getId(), "Ada", "Yılmaz");
        ogrenciEkle(b.getId(), "Berk", "Demir");

        byte[] zip = mockMvc.perform(get("/api/export").with(token(a.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        Icerik icerik = zipOku(zip);
        assertThat(icerik.metin()).contains("Ada");
        assertThat(icerik.metin()).doesNotContain("Berk"); // ASIL KORUNAN DAVRANIS
    }

    @Test
    void zip_tumModulleriIcerir_veIndirilebilirAdlaDoner() throws Exception {
        Tenant t = tenant();

        var response = mockMvc.perform(get("/api/export").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".zip")))
                .andReturn().getResponse();

        Icerik icerik = zipOku(response.getContentAsByteArray());
        assertThat(icerik.dosyalar()).hasSize(16); // OKUBENI + 15 modul
        assertThat(icerik.dosyalar()).contains("00-OKUBENI.txt", "01-ogrenciler.csv",
                "11-tahsilatlar.csv", "15-satislar.csv");
    }

    @Test
    void csv_ExcelUyumlu_BOM_ve_noktaliVirgul() throws Exception {
        Tenant t = tenant();
        ogrenciEkle(t.getId(), "Çiğdem", "Şahin"); // Turkce karakter testi

        byte[] zip = mockMvc.perform(get("/api/export").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!"01-ogrenciler.csv".equals(e.getName())) {
                    continue;
                }
                byte[] icerik = zis.readAllBytes();
                // BOM olmadan Excel Turkce karakterleri bozar.
                assertThat(icerik[0] & 0xFF).isEqualTo(0xEF);
                assertThat(icerik[1] & 0xFF).isEqualTo(0xBB);
                assertThat(icerik[2] & 0xFF).isEqualTo(0xBF);

                String metin = new String(icerik, StandardCharsets.UTF_8);
                assertThat(metin).contains("Ad;Soyad");        // noktali virgul ayrac
                assertThat(metin).contains("Çiğdem;Şahin");    // karakterler bozulmamis
                return;
            }
        }
        throw new AssertionError("01-ogrenciler.csv bulunamadı");
    }

    @Test
    void disaAktarma_DENETIM_izi_birakir() throws Exception {
        Tenant t = tenant();

        mockMvc.perform(get("/api/export").with(token(t.getId(), "ADMIN")))
                .andExpect(status().isOk());

        // GET oldugu icin otomatik interceptor yazmaz; elle yazilan iz burada dogrulanir.
        TenantContext.set(t.getId());
        assertThat(auditRepo.findAll())
                .anyMatch(a -> "Kurum verisi dışa aktarıldı".equals(a.getEylem()));
    }

    @Test
    void yalnizAdmin_disaAktarabilir() throws Exception {
        Tenant t = tenant();

        mockMvc.perform(get("/api/export").with(token(t.getId(), "FRONTDESK")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/export").with(token(t.getId(), "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
