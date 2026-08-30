package com.artademi.reminder;

import com.artademi.common.ApiResponse;
import com.artademi.reminder.dto.BorcluAday;
import com.artademi.reminder.dto.HatirlatmaSonucu;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borclu veli hatirlatmalari — {@code /api/reminders} (ADMIN + FRONTDESK_ACCOUNTING).
 *
 * <p>Yetki neden bu ikisi: borc bilgisi PARASAL veridir; FRONTDESK (on buro) parayi gormez
 * (bkz. yetki matrisi), bu yuzden hatirlatma da gonderemez.
 */
@RestController
@RequestMapping("/api/reminders")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK_ACCOUNTING')")
public class BorcHatirlatmaController {

    private final BorcHatirlatmaService service;

    public BorcHatirlatmaController(BorcHatirlatmaService service) {
        this.service = service;
    }

    /** Borclu ogrenciler + her biri icin gonderilebilirlik durumu. */
    @GetMapping("/candidates")
    public ApiResponse<List<BorcluAday>> adaylar() {
        return ApiResponse.ok(service.adaylar());
    }

    /** Secilen ogrencilerin velilerine hatirlatma gonderir; ogrenci bazinda sonuc doner. */
    @PostMapping
    public ApiResponse<HatirlatmaSonucu> gonder(@RequestBody GonderRequest request) {
        return ApiResponse.ok(service.gonder(request.ogrenciIdleri()));
    }

    public record GonderRequest(
            @NotEmpty(message = "En az bir öğrenci seçmelisiniz") List<Long> ogrenciIdleri) {
    }
}
