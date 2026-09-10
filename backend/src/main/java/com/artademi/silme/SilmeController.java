package com.artademi.silme;

import com.artademi.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yumusak silme uclari — YALNIZCA ADMIN (urun karari). Tenant JWT'den.
 *
 * <ul>
 *   <li>GET  /api/silme/{tur}/{id}/onizleme — modal: engel / etkiler / bagli kayitlar</li>
 *   <li>DELETE /api/silme/{tur}/{id} — damgala (Islem Kaydi'na "silindi" olarak duser)</li>
 *   <li>GET  /api/silme/silinenler?tur= — geri alinabilir kayitlar</li>
 *   <li>POST /api/silme/{tur}/{id}/geri-al</li>
 * </ul>
 * {tur}: ogrenci, grup, egitmen, salon, brans, sube, urun, tedarikci, kasa, tahakkuk, odeme, gider, satis,
 * paket, telafi, basvuru, ders-saati, yoklama-oturumu.
 */
@RestController
@RequestMapping("/api/silme")
@PreAuthorize("hasRole('ADMIN')")
public class SilmeController {

    private final SilmeService service;

    public SilmeController(SilmeService service) {
        this.service = service;
    }

    @GetMapping("/silinenler")
    public ApiResponse<List<SilinenKayit>> silinenler(@RequestParam String tur) {
        return ApiResponse.ok(service.silinenler(SilinebilirTur.fromYol(tur)));
    }

    @GetMapping("/{tur}/{id}/onizleme")
    public ApiResponse<SilmeOnizleme> onizle(@PathVariable String tur, @PathVariable Long id) {
        return ApiResponse.ok(service.onizle(SilinebilirTur.fromYol(tur), id));
    }

    @DeleteMapping("/{tur}/{id}")
    public ApiResponse<Void> sil(@PathVariable String tur, @PathVariable Long id) {
        service.sil(SilinebilirTur.fromYol(tur), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{tur}/{id}/geri-al")
    public ApiResponse<Void> geriAl(@PathVariable String tur, @PathVariable Long id) {
        service.geriAl(SilinebilirTur.fromYol(tur), id);
        return ApiResponse.ok(null);
    }
}
