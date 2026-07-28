package com.artademi.lead;

import com.artademi.common.ApiResponse;
import com.artademi.lead.dto.LeadRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing (artademi.com) iletisim formu ucu — JWT YOK (permitAll), tenant YOK
 * ({@code /api/public/**} interceptor'lardan muaf). Koruma: honeypot + IP soguma + validasyon
 * (bkz. {@link LeadService}). CORS: artademi.com origin'i izinli listede.
 */
@RestController
@RequestMapping("/api/public/leads")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody LeadRequest request,
            HttpServletRequest http) {
        // Caddy arkasindayiz: gercek IP X-Forwarded-For'un ILK degerindedir; yoksa remoteAddr.
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : http.getRemoteAddr();
        service.submit(request, ip);
        return ApiResponse.ok(null);
    }
}
