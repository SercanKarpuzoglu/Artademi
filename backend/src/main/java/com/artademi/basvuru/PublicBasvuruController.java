package com.artademi.basvuru;

import com.artademi.basvuru.dto.BasvuruFormBilgisi;
import com.artademi.basvuru.dto.BasvuruGonderRequest;
import com.artademi.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurumun public on kayit formu — JWT YOK ({@code /api/public/**} permitAll ve
 * interceptor'lardan muaf).
 *
 * <p>⚠️ <b>Tenant burada JWT'den DEGIL, URL'deki slug'dan cozulur.</b> Bu, projenin
 * "tenant yalnizca JWT'den okunur" kuralinin tek istisnasidir; neden guvenli oldugu ve
 * hangi sinirlarla cevrildigi {@link PublicBasvuruService} javadoc'unda ayrintili yazilidir.
 * Ozetle: slug yetki tasimaz, yuzey yalnizca bu iki uctan ibarettir ve kurum AKTIF degilse
 * form 404 doner.
 */
@RestController
@RequestMapping("/api/public/basvuru")
public class PublicBasvuruController {

    private final PublicBasvuruService service;

    public PublicBasvuruController(PublicBasvuruService service) {
        this.service = service;
    }

    /** Formun acilis bilgisi: kurum adi + aktif brans secenekleri. */
    @GetMapping("/{slug}")
    public ApiResponse<BasvuruFormBilgisi> form(@PathVariable String slug) {
        return ApiResponse.ok(service.formBilgisi(slug));
    }

    /** Basvuru gonderimi. Honeypot + IP soguma + mukerrer engeli servistedir. */
    @PostMapping("/{slug}")
    public ApiResponse<Void> gonder(@PathVariable String slug,
            @Valid @RequestBody BasvuruGonderRequest istek, HttpServletRequest http) {
        service.gonder(slug, istek, istemciIp(http));
        return ApiResponse.ok(null);
    }

    /** Caddy arkasindayiz: gercek IP X-Forwarded-For'un ILK degeridir; yoksa remoteAddr. */
    private static String istemciIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : http.getRemoteAddr();
    }
}
