package com.artademi.export;

import com.artademi.audit.TenantAudit;
import com.artademi.audit.TenantAuditRepository;
import com.artademi.common.tenant.TenantContext;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurum verisinin disa aktarimi — {@code GET /api/export} (SADECE ADMIN, kendi kurumu).
 *
 * <p>⚠️ Denetim izi BURADA ELLE yazilir: otomatik interceptor yalnizca DEGISTIRICI istekleri
 * (POST/PUT/PATCH/DELETE) kaydeder, bu ise bir GET. Ama tum kurum verisini disari cikaran
 * hassas bir islem oldugu icin mutlaka iz birakmali — "veriyi kim, ne zaman indirdi" sorusu
 * bir veri sizintisi supheside ilk sorulacak sorudur.
 */
@RestController
@RequestMapping("/api/export")
@PreAuthorize("hasRole('ADMIN')")
public class VeriDisaAktarmaController {

    private static final Logger log = LoggerFactory.getLogger(VeriDisaAktarmaController.class);

    private final VeriDisaAktarmaService service;
    private final TenantAuditRepository audit;

    public VeriDisaAktarmaController(VeriDisaAktarmaService service, TenantAuditRepository audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ResponseEntity<byte[]> disaAktar() throws IOException {
        byte[] zip = service.zipUret();
        izBirak(zip.length);

        String dosya = service.dosyaAdi();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dosya + "\"")
                .body(zip);
    }

    private void izBirak(int boyut) {
        try {
            Jwt jwt = jwt();
            audit.save(TenantAudit.of(TenantContext.get(),
                    jwt == null ? "sistem" : jwt.getClaimAsString("preferred_username"),
                    jwt == null ? null : jwt.getClaimAsString("name"),
                    "Kurum verisi dışa aktarıldı", "GET", "/api/export",
                    (boyut / 1024) + " KB"));
        } catch (RuntimeException e) {
            // Iz yazilamadi diye kullanicinin veri hakki engellenmez; hata loglanir.
            log.error("Dışa aktarma izi yazılamadı: {}", e.getMessage());
        }
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt j ? j : null;
    }
}
