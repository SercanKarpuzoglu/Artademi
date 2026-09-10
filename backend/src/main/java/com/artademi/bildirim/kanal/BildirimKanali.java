package com.artademi.bildirim.kanal;

/**
 * Dis bildirim kanali soyutlamasi (Dalga C). Bugun yalniz e-posta; WhatsApp (Meta Cloud API) ayni
 * arayuzu uygulayarak eklenecek — cagiran kod kanal bilmez.
 */
public interface BildirimKanali {

    /** Kanal adi (log/izleme). */
    String ad();

    /** Kanal kullanilabilir mi (yapilandirilmis mi)? */
    boolean hazir();

    /**
     * Tek alici. Basari true; kanal kapali ya da gonderim hatasi false (istisna FIRLATMAZ — bir
     * bildirimin patlamasi digerlerini durdurmamali).
     */
    boolean gonder(String alici, String konu, String metin);
}
