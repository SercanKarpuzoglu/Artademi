package com.artademi.silme;

import java.util.List;

/**
 * Silme onizlemesi (uyari modali icin): silinebilir mi, degilse neden; silinince ne olacak (etkiler);
 * bilgi amacli bagli kayit sayilari (silinmez, gizli kalir ve para izi korunur).
 */
public record SilmeOnizleme(
        String tur,
        Long id,
        String ad,
        boolean silinebilir,
        String engel,
        List<String> etkiler,
        List<BagliKayit> bagliKayitlar) {

    public record BagliKayit(String ad, long sayi) {
    }
}
