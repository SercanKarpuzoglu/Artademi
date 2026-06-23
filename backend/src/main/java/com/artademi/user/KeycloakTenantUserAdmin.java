package com.artademi.user;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.platform.TenantUserAdmin;
import com.artademi.platform.dto.CreateTenantUserRequest;
import com.artademi.platform.dto.TenantUserView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link TenantUserAdmin}'in Keycloak uygulamasi (platform konsolu icin). {@code user} paketinde
 * oldugundan {@link KeycloakAdminClient}'in paket-ozel metotlarina ve {@link UserService#MANAGEABLE_ROLES}'a
 * dogrudan erisir (kopya yok). Tenant DISARIDAN ({@code tenantId}) gelir — SUPER_ADMIN tenant'sizdir.
 *
 * <p>Izolasyon fail-closed: liste yalnizca {@code tenant_id} attribute'u eslesen kullanicilari verir;
 * silmede hedefin tenant'i path tenant'iyla eslesmezse 404 (sizinti yok). Yeni kullanici: sabit ilk
 * parola {@code Artademi2026!} + {@code must_change_password=true}; roller atanabilir kumeyle sinirli.
 */
@Service
public class KeycloakTenantUserAdmin implements TenantUserAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTenantUserAdmin.class);

    private static final String FIRST_PASSWORD = "Artademi2026!";
    private static final String ATTR_TENANT = "tenant_id";
    private static final String ATTR_TELEFON = "telefon";
    private static final String ATTR_MUST_CHANGE = "must_change_password";
    private static final int MAX_USERS = 200;

    private final KeycloakAdminClient kc;

    public KeycloakTenantUserAdmin(KeycloakAdminClient kc) {
        this.kc = kc;
    }

    @Override
    public List<TenantUserView> list(UUID tenantId) {
        String tenant = tenantId.toString();
        List<TenantUserView> out = new ArrayList<>();
        for (Map<String, Object> rep : kc.searchUsers(null, null, 0, MAX_USERS, tenant)) {
            // KC q ile filtrelendi; yine de fail-closed dogrula.
            if (!tenant.equals(KeycloakAdminClient.firstAttribute(rep, ATTR_TENANT))) {
                continue;
            }
            out.add(toView(rep));
        }
        return out;
    }

    @Override
    public TenantUserView create(UUID tenantId, CreateTenantUserRequest req) {
        Set<String> roller = validateRoles(req.roller());
        String tenant = tenantId.toString();

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ATTR_TENANT, List.of(tenant)); // tenant_id PATH'ten — body'den DEGIL
        if (hasText(req.telefon())) {
            attrs.put(ATTR_TELEFON, List.of(req.telefon()));
        }
        attrs.put(ATTR_MUST_CHANGE, List.of("true"));

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", req.kullaniciAdi());
        rep.put("firstName", req.ad());
        rep.put("lastName", req.soyad());
        if (hasText(req.email())) {
            rep.put("email", req.email());
        }
        rep.put("enabled", true);
        rep.put("attributes", attrs);

        String newId = kc.createUser(rep); // 409 (yinelenen) -> ConflictException
        try {
            kc.resetPassword(newId, FIRST_PASSWORD, false);
            assignRoles(newId, roller);
        } catch (RuntimeException e) {
            // Oksuz kullanici birakma: olusan kullaniciyi geri al.
            try {
                kc.deleteUser(newId);
            } catch (RuntimeException cleanup) {
                log.error("Kullanici olusturma geri alma (deleteUser) basarisiz: {}", newId, cleanup);
            }
            throw e;
        }
        return toView(kc.getUserById(newId));
    }

    @Override
    public void delete(UUID tenantId, String userId) {
        Map<String, Object> rep = kc.getUserById(userId);
        if (rep == null
                || !tenantId.toString().equals(KeycloakAdminClient.firstAttribute(rep, ATTR_TENANT))) {
            // Baska tenant'a ait veya yok -> var olmayan gibi davran (sizinti yok).
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        kc.deleteUser(userId);
    }

    // ------------------------------------------------------------------ yardimcilar

    private TenantUserView toView(Map<String, Object> rep) {
        String id = String.valueOf(rep.get("id"));
        return new TenantUserView(
                id,
                str(rep, "username"),
                str(rep, "firstName"),
                str(rep, "lastName"),
                str(rep, "email"),
                KeycloakAdminClient.firstAttribute(rep, ATTR_TELEFON),
                manageableRolesOf(id),
                boolVal(rep, "enabled"));
    }

    private List<String> manageableRolesOf(String id) {
        List<String> out = new ArrayList<>();
        for (String r : KeycloakAdminClient.roleNames(kc.getUserRealmRoles(id))) {
            if (UserService.MANAGEABLE_ROLES.contains(r)) {
                out.add(r);
            }
        }
        return out;
    }

    private Set<String> validateRoles(List<String> roller) {
        Set<String> desired = new LinkedHashSet<>(roller);
        for (String r : desired) {
            if (!UserService.MANAGEABLE_ROLES.contains(r)) {
                throw new ValidationException("Geçersiz veya atanamaz rol: " + r);
            }
        }
        return desired;
    }

    private void assignRoles(String userId, Set<String> roller) {
        List<Map<String, Object>> reps = new ArrayList<>();
        for (String name : roller) {
            Map<String, Object> role = kc.getRealmRole(name);
            if (role == null || role.get("id") == null) {
                throw new ValidationException("Rol bulunamadı: " + name);
            }
            reps.add(Map.of("id", role.get("id"), "name", role.get("name")));
        }
        kc.addRealmRoles(userId, reps);
    }

    private static String str(Map<String, Object> rep, String key) {
        Object v = rep.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean boolVal(Map<String, Object> rep, String key) {
        Object v = rep.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
