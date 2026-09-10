package com.artademi.kredi;

import com.artademi.common.ApiResponse;
import com.artademi.enrollment.OdemePlani;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gruba yazarken plan secim onizlemesi (ofis rolleri; ucret gosterir — on buro kayit yaparken gorur). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class KrediController {

    private final KrediService service;

    public KrediController(KrediService service) {
        this.service = service;
    }

    @GetMapping("/api/groups/{id}/kayit-onizleme")
    public ApiResponse<KayitOnizleme> onizle(@PathVariable Long id, @RequestParam OdemePlani plan,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tarih) {
        return ApiResponse.ok(service.onizle(id, plan, tarih));
    }
}
