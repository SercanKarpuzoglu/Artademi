package com.artademi.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SUNUCU TARAFI ilk-parola yaptirimi.
 *
 * <p><b>Neden var:</b> {@code must_change_password} yalnizca tarayicida kontrol ediliyordu
 * ({@code AppShell} kilit ekrani). Bu bir guvenlik kontrolu DEGIL, UX durtmesiydi: istegi
 * dogrudan API'ye atan biri parolasini hic degistirmeden her seye erisebiliyordu. Artik
 * yaptirim tek kapidan, sunucuda yapilir.
 *
 * <p><b>Muaf uclar</b> yalnizca kullanicinin bu durumdan CIKABILMESI icin gerekli olanlardir
 * (profil oku, parola degistir). Aksi halde kullanici kilitli kalir ve cikis yolu bulamaz.
 *
 * <p><b>Onbellek:</b> bayrak Keycloak ozniteliginde; her istekte Keycloak'a gitmek pahali
 * olurdu. {@value #TTL_SANIYE} sn TTL + parola degisiminde ACIK invalidasyon kullanilir —
 * boylece kullanici parolasini degistirir degistirmez kilit kalkar, TTL beklenmez.
 */
@Component
public class ParolaDegisikligiInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ParolaDegisikligiInterceptor.class);

    static final long TTL_SANIYE = 30;

    private final KeycloakAdminClient kc;
    private final CurrentUser currentUser;

    /** sub → (bayrak, gecerlilik sonu) */
    private final Map<String, Onbellek> onbellek = new ConcurrentHashMap<>();

    public ParolaDegisikligiInterceptor(KeycloakAdminClient kc, CurrentUser currentUser) {
        this.kc = kc;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull Object handler) {
        String sub = currentUser.sub();
        if (sub == null) {
            return true; // kimlik yok (permitAll uclar) → bu kontrol anlamsiz
        }
        if (degistirmesiGerekiyor(sub)) {
            throw new ParolaDegisikligiGerekliException(
                    "Devam etmek için ilk parolanızı değiştirmeniz gerekiyor.");
        }
        return true;
    }

    /** Parola degistiginde cagrilir: kullanici TTL beklemeden devam edebilsin. */
    public void temizle(String sub) {
        onbellek.remove(sub);
    }

    private boolean degistirmesiGerekiyor(String sub) {
        Onbellek mevcut = onbellek.get(sub);
        if (mevcut != null && Instant.now().isBefore(mevcut.gecerlilikSonu())) {
            return mevcut.gerekli();
        }
        boolean gerekli;
        try {
            Map<String, Object> rep = kc.getUserById(sub);
            gerekli = rep != null && "true".equalsIgnoreCase(
                    KeycloakAdminClient.firstAttribute(rep, "must_change_password"));
        } catch (RuntimeException e) {
            // Keycloak'a ulasilamiyorsa KULLANICIYI KILITLEME: erisimi kesmek, bilinmeyen bir
            // altyapi hatasi yuzunden calisan kurumu durdurmak olurdu. Hata loglanir.
            log.error("must_change_password okunamadı (sub={}): {}", sub, e.getMessage());
            return false;
        }
        onbellek.put(sub, new Onbellek(gerekli, Instant.now().plus(Duration.ofSeconds(TTL_SANIYE))));
        if (onbellek.size() > 10_000) {
            onbellek.clear(); // naif temizlik; bellek sinirsiz buyumesin
        }
        return gerekli;
    }

    private record Onbellek(boolean gerekli, Instant gecerlilikSonu) {
    }
}
