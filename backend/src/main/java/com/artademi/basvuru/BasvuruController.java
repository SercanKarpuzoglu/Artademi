package com.artademi.basvuru;

import com.artademi.basvuru.dto.BasvuruResponse;
import com.artademi.basvuru.dto.DurumGuncelleRequest;
import com.artademi.basvuru.dto.OgrenciyeDonusturRequest;
import com.artademi.common.ApiResponse;
import com.artademi.common.PageMeta;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurum ici basvuru yonetimi.
 *
 * <p>Yetki ADMIN + on buro rolleri: on kayit takibi ON BURO isidir ve basvuruda parasal
 * bilgi yoktur.
 */
@RestController
@RequestMapping("/api/basvurular")
@PreAuthorize("hasAnyRole('ADMIN','FRONTDESK','FRONTDESK_ACCOUNTING')")
public class BasvuruController {

    private final BasvuruService service;

    public BasvuruController(BasvuruService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<BasvuruResponse>> list(
            @RequestParam(required = false) BasvuruDurumu durum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Proje sozlesmesi: data = duz dizi, sayfalama meta'da (bkz. diger liste uclari).
        Page<BasvuruResponse> sonuc = service.list(durum, PageRequest.of(page, size));
        return ApiResponse.ok(sonuc.getContent(), PageMeta.of(sonuc));
    }

    /** Panel rozeti icin: ilgilenilmemis basvuru sayisi. */
    @GetMapping("/yeni-sayisi")
    public ApiResponse<Long> yeniSayisi() {
        return ApiResponse.ok(service.yeniSayisi());
    }

    @GetMapping("/{id}")
    public ApiResponse<BasvuruResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/{id}/durum")
    public ApiResponse<BasvuruResponse> durum(@PathVariable Long id,
            @Valid @RequestBody DurumGuncelleRequest istek) {
        return ApiResponse.ok(service.durumGuncelle(id, istek.durum()));
    }

    /** Basvuruyu ogrenci kaydina donusturur (ek zorunlu alanlar istekte gelir). */
    @PostMapping("/{id}/ogrenciye-donustur")
    public ApiResponse<BasvuruResponse> ogrenciyeDonustur(@PathVariable Long id,
            @Valid @RequestBody OgrenciyeDonusturRequest istek) {
        return ApiResponse.ok(service.ogrenciyeDonustur(id, istek));
    }
}
