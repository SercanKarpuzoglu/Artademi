package com.artademi.audit;

import com.artademi.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Her BASARILI degistirici istegi kurum islem kaydina yazar.
 *
 * <p><b>Neden interceptor:</b> servisleri tek tek isaretlemek yerine tek noktadan yakalanir —
 * yeni bir modul eklendiginde "iz birakmayi unutma" riski YOKTUR, kapsama kendiliginden tamdir.
 *
 * <p>Kayit KURALLARI:
 * <ul>
 *   <li>Yalnizca POST/PUT/PATCH/DELETE (okuma islemleri iz birakmaz — gurultu olurdu).</li>
 *   <li>Yalnizca 2xx (basarisiz/yetkisiz denemeler kaydi kirletmez).</li>
 *   <li>Tenant baglami YOKSA yazilmaz (platform/webhook yollari zaten haric).</li>
 *   <li>Yazma hatasi ISTEGI ETKILEMEZ: kullanicinin isi, denetim kaydi yuzunden patlamamali.</li>
 * </ul>
 */
@Component
public class TenantAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantAuditInterceptor.class);

    /** Yol parcasi → insan tarafindan okunur modul adi. */
    private static final Map<String, String> MODUL = new LinkedHashMap<>();

    static {
        MODUL.put("students", "Öğrenci");
        MODUL.put("groups", "Grup");
        MODUL.put("enrollments", "Kayıt");
        MODUL.put("attendance-sessions", "Yoklama");
        MODUL.put("schedules", "Program");
        MODUL.put("branches", "Branş");
        MODUL.put("rooms", "Salon");
        MODUL.put("teachers", "Öğretmen");
        MODUL.put("accruals", "Tahakkuk");
        MODUL.put("payments", "Tahsilat");
        MODUL.put("expenses", "Gider");
        MODUL.put("payouts", "Hakediş");
        MODUL.put("products", "Ürün");
        MODUL.put("sales", "Satış");
        MODUL.put("users", "Kullanıcı");
        MODUL.put("me", "Profil");
        MODUL.put("billing", "Abonelik");
        MODUL.put("tenant", "Kurum");
    }

    private final TenantAuditRepository repository;

    public TenantAuditInterceptor(TenantAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        try {
            if (!yazilmali(request, response)) {
                return;
            }
            UUID tenantId = TenantContext.get();
            if (tenantId == null) {
                return;
            }
            String yol = request.getRequestURI();
            repository.save(TenantAudit.of(tenantId, actor(), actorAd(),
                    eylem(request.getMethod(), yol), request.getMethod(), yol, kayitId(yol)));
        } catch (RuntimeException e) {
            // Denetim yazimi kullanicinin islemini ASLA bozmamali (islem zaten tamamlandi).
            log.error("İşlem kaydı yazılamadı ({} {}): {}", request.getMethod(),
                    request.getRequestURI(), e.getMessage());
        }
    }

    private static boolean yazilmali(HttpServletRequest request, HttpServletResponse response) {
        String m = request.getMethod();
        boolean degistirici = "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)
                || "DELETE".equals(m);
        boolean basarili = response.getStatus() >= 200 && response.getStatus() < 300;
        return degistirici && basarili;
    }

    /** "Öğrenci eklendi" gibi okunur eylem uretir. */
    static String eylem(String metot, String yol) {
        String modul = modulAdi(yol);
        String fiil = switch (metot) {
            case "POST" -> yol.endsWith("/active") || yol.contains("/status") ? "durumu değiştirildi"
                    : "eklendi";
            case "PUT" -> "güncellendi";
            case "PATCH" -> "durumu değiştirildi";
            case "DELETE" -> "silindi";
            default -> "işlendi";
        };
        return modul + " " + fiil;
    }

    private static String modulAdi(String yol) {
        for (Map.Entry<String, String> e : MODUL.entrySet()) {
            if (yol.contains("/" + e.getKey())) {
                return e.getValue();
            }
        }
        return "Kayıt";
    }

    /** Yoldaki son sayisal/UUID parca = islem goren kaydin kimligi. */
    static String kayitId(String yol) {
        String[] parcalar = yol.split("/");
        for (int i = parcalar.length - 1; i >= 0; i--) {
            String p = parcalar[i];
            if (p.isBlank()) {
                continue;
            }
            if (p.chars().allMatch(Character::isDigit) || p.length() == 36 && p.contains("-")) {
                return p;
            }
        }
        return null;
    }

    private static String actor() {
        Jwt jwt = jwt();
        if (jwt == null) {
            return "sistem";
        }
        String u = jwt.getClaimAsString("preferred_username");
        return u == null || u.isBlank() ? "sistem" : u;
    }

    private static String actorAd() {
        Jwt jwt = jwt();
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt j ? j : null;
    }
}
