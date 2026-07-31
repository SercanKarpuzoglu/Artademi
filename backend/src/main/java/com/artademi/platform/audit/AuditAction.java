package com.artademi.platform.audit;

/**
 * Denetim izine yazilan platform islemleri. DB'de metin olarak saklanir (yeni tip eklemek
 * migration gerektirmez); yalnizca GERI DONUSU OLAN/sorumluluk doguran islemler kaydedilir —
 * okuma/listeleme kaydedilmez (iz gurultuye bogulmasin).
 */
public enum AuditAction {

    /** Yeni kurum acildi (ilk admin provisioning dahil). */
    KURUM_OLUSTURULDU("Kurum oluşturuldu"),
    /** Kurum durumu degistirildi (AKTIF ↔ ASKIDA). */
    KURUM_DURUMU_DEGISTI("Kurum durumu değişti"),
    /** Kurum soft-delete edildi (SILINDI). */
    KURUM_SILINDI("Kurum silindi"),
    /** Kuruma kullanici eklendi. */
    KULLANICI_EKLENDI("Kullanıcı eklendi"),
    /** Kurumdan kullanici silindi. */
    KULLANICI_SILINDI("Kullanıcı silindi"),
    /** Abonelik/odeme elle guncellendi (markPaid, donem ilerletme). */
    ABONELIK_GUNCELLENDI("Abonelik güncellendi");

    private final String etiket;

    AuditAction(String etiket) {
        this.etiket = etiket;
    }

    /** Konsolda gosterilen Turkce etiket. */
    public String etiket() {
        return etiket;
    }
}
