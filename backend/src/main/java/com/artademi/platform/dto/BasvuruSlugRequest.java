package com.artademi.platform.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kurumun public on kayit baglanti adini belirlemesi.
 *
 * <p>{@code null}/bos gonderimi ozelligi KAPATIR (form 404 doner) — kurum baglantisini
 * iptal edebilmelidir.
 *
 * <p>Bicim URL'de gorunecegi icin dar tutulur: kucuk harf, rakam ve tire. Turkce karakter
 * ve bosluk KABUL EDILMEZ (URL'de bozulur, paylasilan baglanti calismaz).
 */
public record BasvuruSlugRequest(
        @Size(min = 3, max = 60, message = "Bağlantı adı 3-60 karakter olmalıdır")
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "Sadece küçük harf, rakam ve tire kullanın (örn: bale-akademi)")
        String slug) {

    /** Bos gonderim = ozelligi kapat. */
    public boolean kapatiliyorMu() {
        return slug == null || slug.isBlank();
    }
}
