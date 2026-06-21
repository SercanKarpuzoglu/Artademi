package com.artademi.user;

import com.artademi.platform.TenantAdminProvisioner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link TenantAdminProvisioner}'in Keycloak uygulamasi. {@code user} paketindedir; bu yuzden
 * {@link KeycloakAdminClient}'in paket-ozel metotlarina dogrudan erisir (Keycloak protokolu burada
 * konusulur, is kurali degil).
 *
 * <p>Yeni admin kalibi (mevcut kullanici-yaratma kaliginin aynisi): sabit ilk parola
 * {@code Artademi2026!} + {@code must_change_password=true}, realm rolu {@code ADMIN}, attribute
 * {@code tenant_id} = yeni tenant id'si. Keycloak temsili TAM gonderilir (createUser zaten boyle).
 *
 * <p><b>Sahipsiz kullanici garantisi:</b> {@code createUser} 409 (yinelenen username/email) atarsa
 * hic kullanici olusmaz. createUser BASARILI olup parola/rol adimi patlarsa, olusan kullanici
 * silinir ve hata yukari firlatilir; boylece yarim kalmis (oksuz) kullanici birakilmaz.
 */
@Service
public class KeycloakTenantAdminProvisioner implements TenantAdminProvisioner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTenantAdminProvisioner.class);

    /** Yeni kullanicilara verilen sabit ilk parola (gecici DEGIL; must_change_password ile zorlanir). */
    private static final String FIRST_PASSWORD = "Artademi2026!";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ATTR_TENANT = "tenant_id";
    private static final String ATTR_MUST_CHANGE = "must_change_password";

    private final KeycloakAdminClient kc;

    public KeycloakTenantAdminProvisioner(KeycloakAdminClient kc) {
        this.kc = kc;
    }

    @Override
    public ProvisionedAdmin provision(UUID tenantId, String email, String ad, String soyad) {
        String tenant = tenantId.toString();
        String username = deriveUsername(email, tenant);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ATTR_TENANT, List.of(tenant));
        attrs.put(ATTR_MUST_CHANGE, List.of("true"));

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", username);
        rep.put("firstName", ad);
        rep.put("lastName", soyad);
        rep.put("email", email);
        rep.put("enabled", true);
        rep.put("attributes", attrs);

        // 409 (yinelenen username/email) -> ConflictException; bu noktada kullanici OLUSMAZ.
        String newId = kc.createUser(rep);
        try {
            kc.resetPassword(newId, FIRST_PASSWORD, false);
            assignAdminRole(newId);
        } catch (RuntimeException e) {
            // Kullanici olustu ama sonraki adim patladi -> oksuz birakma, geri al.
            try {
                kc.deleteUser(newId);
            } catch (RuntimeException cleanup) {
                log.error("Provisioning geri alma (deleteUser) basarisiz: userId={}", newId, cleanup);
            }
            throw e;
        }
        return new ProvisionedAdmin(username, email);
    }

    /**
     * E-postanin {@code @} oncesinden Keycloak-uyumlu bir username turetir. Cakisma varsa tenant
     * id'sinin ilk 4, hala varsa 8 hex hanesini ekler (benzersizlestirme).
     */
    private String deriveUsername(String email, String tenantId) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String base = local.toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) {
            base = "admin";
        }
        if (!kc.usernameExists(base)) {
            return base;
        }
        String hex = tenantId.replace("-", "");
        String c4 = base + "." + hex.substring(0, 4);
        if (!kc.usernameExists(c4)) {
            return c4;
        }
        return base + "." + hex.substring(0, 8);
    }

    /** Realm {@code ADMIN} rolunu kullaniciya ekler; rol yoksa hata. */
    private void assignAdminRole(String userId) {
        Map<String, Object> role = kc.getRealmRole(ROLE_ADMIN);
        if (role == null || role.get("id") == null) {
            throw new IllegalStateException("Realm rolü bulunamadı: " + ROLE_ADMIN);
        }
        kc.addRealmRoles(userId, List.of(Map.of("id", role.get("id"), "name", role.get("name"))));
    }
}
