package com.artademi.platform;

import java.util.UUID;

/**
 * Yeni bir tenant'in ilk ADMIN kullanicisini kimlik saglayicida (Keycloak) provision eden port.
 *
 * <p>Bu arayuz {@code platform} paketinde tanimlidir; somut uygulama {@code user} paketindedir
 * ({@code KeycloakTenantAdminProvisioner}). Bagimlilik yonu boyle olunca {@code platform} ->
 * {@code user} derleme dongusu olusmaz (zaten {@code user} -> {@code platform} bagimliligi var) ve
 * platform testlerinde agla cikmadan {@code @MockBean} ile taklit edilebilir.
 *
 * <p>Sozlesme: basarisizlikta {@link RuntimeException} firlatir (cagiran tarafta yakalanip tenant
 * korunur). Kismi basari (kullanici olustu ama rol/parola adimi patladi) durumunda uygulama olusan
 * kullaniciyi geri almali ki sahipsiz (oksuz) kullanici kalmasin.
 */
public interface TenantAdminProvisioner {

    /**
     * Verilen tenant icin bir ADMIN kullanicisi yaratir (parola sabit ilk-parola, must_change_password
     * = true, realm rolu ADMIN, attribute tenant_id = {@code tenantId}).
     *
     * @param tenantId yeni tenant'in id'si (yeni admin'in tenant_id'si — istemciden DEGIL)
     * @param email    yonetici e-postasi (username bundan turetilir)
     * @param ad       yonetici adi (firstName)
     * @param soyad    yonetici soyadi (lastName)
     * @return turetilen username + email
     */
    ProvisionedAdmin provision(UUID tenantId, String email, String ad, String soyad);

    /** Provision sonucu: kullaniciya hangi username ile girecegi bildirilmeli. */
    record ProvisionedAdmin(String username, String email) {
    }
}
