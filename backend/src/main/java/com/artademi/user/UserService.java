package com.artademi.user;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.TenantRequiredException;
import com.artademi.common.exception.ValidationException;
import com.artademi.user.dto.ChangePasswordRequest;
import com.artademi.user.dto.CreateUserRequest;
import com.artademi.user.dto.MeResponse;
import com.artademi.user.dto.UpdateMeRequest;
import com.artademi.user.dto.UpdateUserRequest;
import com.artademi.user.dto.UserResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Kullanici yonetimi is kurallari. Keycloak protokolu {@link KeycloakAdminClient}'tedir; bu sinif
 * COK KIRACILILIK ve ROL izolasyonunu enforce eder (multi-tenancy / keycloak-auth — pazarlik yok).
 *
 * <p><b>Tenant izolasyonu (fail-closed):</b> acting admin'in tenant'i {@code TenantContext.get()};
 * null ise {@link TenantRequiredException}. Tekil her islemde (get/update/active/delete) hedef
 * kullanicinin {@code tenant_id} attribute'u acting admin'in tenant'iyla esitlenmek ZORUNDA; aksi
 * halde {@link NotFoundException} (404, sizinti yok). Create'te yeni kullanicinin tenant_id'si
 * istemciden DEGIL, acting admin'den set edilir.
 *
 * <p><b>Rol kurali:</b> yalnizca {@link #MANAGEABLE_ROLES} atanabilir; SUPER_ADMIN asla. Rol
 * uzlastirmasi SADECE manageable kume uzerinde calisir; default-roles/offline_access vb. dokunulmaz.
 *
 * <p><b>must_change_password kilidi:</b> Bu modul SADECE {@code mustChangePassword} bayragini
 * /api/me'de gosterir; diger uclari bloke eden global bir interceptor EKLENMEZ (frontend enforce
 * eder). Ileride tam backend kilidi olasi bir sertlestirme olarak eklenebilir.
 */
@Service
public class UserService {

    /** Atanabilir realm rolleri; SUPER_ADMIN bu kumede DEGILDIR (asla atanmaz). */
    static final Set<String> MANAGEABLE_ROLES =
            Set.of("ADMIN", "FRONTDESK", "FRONTDESK_ACCOUNTING", "TEACHER");

    private static final String ATTR_TENANT = "tenant_id";
    private static final String ATTR_TELEFON = "telefon";
    private static final String ATTR_MUST_CHANGE = "must_change_password";

    /** Yeni kullanicilara verilen sabit ilk parola (gecici DEGIL; must_change_password ile zorlanir). */
    private static final String FIRST_PASSWORD = "Artademi2026!";

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final KeycloakAdminClient kc;
    private final CurrentUser currentUser;
    private final com.artademi.platform.TenantService tenantService;
    private final com.artademi.platform.SubscriptionService subscriptionService;

    public UserService(KeycloakAdminClient kc, CurrentUser currentUser,
            com.artademi.platform.TenantService tenantService,
            com.artademi.platform.SubscriptionService subscriptionService) {
        this.kc = kc;
        this.currentUser = currentUser;
        this.tenantService = tenantService;
        this.subscriptionService = subscriptionService;
    }

    // =====================================================================
    // /api/users (ADMIN)
    // =====================================================================

    /** Acting admin'in tenant'indaki kullanicilari listeler; {@code rol} verilirse uygulamada filtreler. */
    public List<UserResponse> list(Boolean aktif, String rol, String q, int page, int size) {
        String tenant = requireTenant();
        int first = page * size;
        List<Map<String, Object>> users = kc.searchUsers(q, aktif, first, size, tenant);

        List<UserResponse> result = new ArrayList<>();
        for (Map<String, Object> rep : users) {
            // Tenant filtresi KC q ile yapildi; yine de fail-closed dogrula.
            if (!tenant.equals(KeycloakAdminClient.firstAttribute(rep, ATTR_TENANT))) {
                continue;
            }
            List<String> roller = manageableRolesOf(stringId(rep));
            if (rol != null && !rol.isBlank() && !roller.contains(rol)) {
                continue;
            }
            result.add(toResponse(rep, roller));
        }
        return result;
    }

    /** Tek kullanici (tenant-eslesme zorunlu; baska tenant -> 404, sizinti yok). */
    public UserResponse get(String id) {
        requireSameTenant(id);
        return loadResponse(id);
    }

    /** Yeni kullanici olusturur; tenant_id acting admin'den set edilir. */
    public UserResponse create(CreateUserRequest req) {
        String tenant = requireTenant();
        Set<String> roller = validateRoles(req.roller());

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(ATTR_TENANT, List.of(tenant));
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

        String newId = kc.createUser(rep); // KC 409 -> ConflictException
        kc.resetPassword(newId, FIRST_PASSWORD, false);
        assignRoles(newId, roller);

        return loadResponse(newId);
    }

    /** Kullaniciyi gunceller (tenant-eslesme zorunlu; rol uzlastirmasi). */
    public UserResponse update(String id, UpdateUserRequest req) {
        Map<String, Object> existing = requireSameTenant(id);
        Set<String> desired = validateRoles(req.roller());

        // tenant_id ve must_change_password attribute'lari KORUNUR; telefon guncellenir.
        Map<String, Object> attrs = KeycloakAdminClient.copyAttributes(existing);
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("firstName", req.ad());
        rep.put("lastName", req.soyad());
        if (hasText(req.email())) {
            rep.put("email", req.email());
        }
        putAttr(attrs, ATTR_TELEFON, req.telefon());
        rep.put("attributes", attrs);

        kc.updateUser(id, rep);
        reconcileRoles(id, desired);
        return loadResponse(id);
    }

    /** Aktiflik degisikligi; kendi hesabini pasife alamaz. */
    public UserResponse changeActive(String id, boolean aktif) {
        requireSameTenant(id);
        if (id.equals(currentUser.sub())) {
            throw new ValidationException("Kendi hesabınızı pasife alamazsınız");
        }
        kc.setEnabled(id, aktif);
        return loadResponse(id);
    }

    /** Kullaniciyi siler; kendi hesabini silemez. */
    public void delete(String id) {
        requireSameTenant(id);
        if (id.equals(currentUser.sub())) {
            throw new ValidationException("Kendi hesabınızı silemezsiniz");
        }
        kc.deleteUser(id);
    }

    // =====================================================================
    // /api/me (her rol)
    // =====================================================================

    /** Oturum sahibinin profili. */
    public MeResponse me() {
        String sub = currentUser.sub();
        Map<String, Object> rep = kc.getUserById(sub);
        if (rep == null) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        boolean mustChange = "true".equalsIgnoreCase(
                KeycloakAdminClient.firstAttribute(rep, ATTR_MUST_CHANGE));
        String tenantId = tenantOf(rep);
        var subscriptionWarning = tenantId == null
                ? null
                : subscriptionService.warningFor(UUID.fromString(tenantId));
        return new MeResponse(
                sub,
                stringValue(rep, "username"),
                stringValue(rep, "firstName"),
                stringValue(rep, "lastName"),
                stringValue(rep, "email"),
                KeycloakAdminClient.firstAttribute(rep, ATTR_TELEFON),
                currentUser.realmRoles(),
                mustChange,
                tenantId,
                tenantService.currentName(),
                subscriptionWarning);
    }

    /** Kullanicinin tenant_id attribute'u (varsa). */
    private static String tenantOf(Map<String, Object> rep) {
        return KeycloakAdminClient.firstAttribute(rep, ATTR_TENANT);
    }

    /** Oturum sahibinin profilini gunceller (rol/tenant_id/must_change_password DEGISMEZ). */
    public MeResponse updateMe(UpdateMeRequest req) {
        String sub = currentUser.sub();
        Map<String, Object> existing = kc.getUserById(sub);
        if (existing == null) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        Map<String, Object> attrs = KeycloakAdminClient.copyAttributes(existing);
        putAttr(attrs, ATTR_TELEFON, req.telefon());

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("firstName", req.ad());
        rep.put("lastName", req.soyad());
        if (hasText(req.email())) {
            rep.put("email", req.email());
        }
        rep.put("attributes", attrs);
        kc.updateUser(sub, rep);
        return me();
    }

    /** Parola degistirir: mevcut parolayi dogrular, yeni parolayi yazar, must_change_password=false. */
    public void changePassword(ChangePasswordRequest req) {
        String sub = currentUser.sub();
        String username = currentUser.username();
        if (!kc.verifyPassword(username, req.mevcutParola())) {
            throw new ValidationException("Mevcut parola hatalı");
        }
        if (req.yeniParola() == null || req.yeniParola().length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Yeni parola en az " + MIN_PASSWORD_LENGTH + " karakter olmalı");
        }
        kc.resetPassword(sub, req.yeniParola(), false);

        // must_change_password=false; diger attribute'lar korunur.
        Map<String, Object> existing = kc.getUserById(sub);
        Map<String, Object> attrs = KeycloakAdminClient.copyAttributes(existing);
        attrs.put(ATTR_MUST_CHANGE, List.of("false"));
        kc.updateUser(sub, Map.of("attributes", attrs));
    }

    // =====================================================================
    // Dahili yardimcilar
    // =====================================================================

    /** Acting admin'in tenant'i; yoksa fail-closed. */
    private String requireTenant() {
        UUID tenant = com.artademi.common.tenant.TenantContext.get();
        if (tenant == null) {
            throw new TenantRequiredException("Tenant bağlamı yok");
        }
        return tenant.toString();
    }

    /**
     * Hedef kullaniciyi getirir ve tenant'inin acting admin'le ayni olmasini zorunlu kilar; aksi
     * halde 404 (var olmayan gibi davranir, sizinti yok). Kullaniciyi (rep) doner.
     */
    private Map<String, Object> requireSameTenant(String id) {
        String tenant = requireTenant();
        Map<String, Object> rep = kc.getUserById(id);
        if (rep == null) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        if (!tenant.equals(KeycloakAdminClient.firstAttribute(rep, ATTR_TENANT))) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        return rep;
    }

    /** roller bos olmamali ve tamami MANAGEABLE_ROLES icinde olmali; aksi halde 400. */
    private Set<String> validateRoles(List<String> roller) {
        if (roller == null || roller.isEmpty()) {
            throw new ValidationException("En az bir rol seçilmeli");
        }
        Set<String> desired = new LinkedHashSet<>(roller);
        for (String r : desired) {
            if (!MANAGEABLE_ROLES.contains(r)) {
                throw new ValidationException("Geçersiz veya atanamaz rol: " + r);
            }
        }
        return desired;
    }

    /** Verilen rolleri kullaniciya ekler (yeni kullanici icin). */
    private void assignRoles(String id, Set<String> roller) {
        List<Map<String, Object>> toAdd = resolveRoleReps(roller);
        kc.addRealmRoles(id, toAdd);
    }

    /**
     * Manageable rol kumesini istenen kume olacak sekilde uzlastirir: fazla olanlar kaldirilir,
     * eksik olanlar eklenir. Manageable OLMAYAN roller (default-roles/offline_access vb.) DOKUNULMAZ.
     */
    private void reconcileRoles(String id, Set<String> desired) {
        List<String> currentManageable = manageableRolesOf(id);
        Set<String> currentSet = new LinkedHashSet<>(currentManageable);

        Set<String> toRemove = new LinkedHashSet<>(currentSet);
        toRemove.removeAll(desired);
        Set<String> toAdd = new LinkedHashSet<>(desired);
        toAdd.removeAll(currentSet);

        kc.removeRealmRoles(id, resolveRoleReps(toRemove));
        kc.addRealmRoles(id, resolveRoleReps(toAdd));
    }

    /** Rol adlarini KC realm rol temsillerine ({id,name}) cevirir; bulunamayan rol -> 400. */
    private List<Map<String, Object>> resolveRoleReps(Set<String> roller) {
        List<Map<String, Object>> reps = new ArrayList<>();
        for (String name : roller) {
            Map<String, Object> role = kc.getRealmRole(name);
            if (role == null || role.get("id") == null) {
                throw new ValidationException("Rol bulunamadı: " + name);
            }
            reps.add(Map.of("id", role.get("id"), "name", role.get("name")));
        }
        return reps;
    }

    /** Kullanicinin SADECE manageable realm rollerini (adlarini) doner. */
    private List<String> manageableRolesOf(String id) {
        List<String> all = KeycloakAdminClient.roleNames(kc.getUserRealmRoles(id));
        List<String> out = new ArrayList<>();
        for (String r : all) {
            if (MANAGEABLE_ROLES.contains(r)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Tekil kullanici yanitini id'den yukler (rep + manageable roller). */
    private UserResponse loadResponse(String id) {
        Map<String, Object> rep = kc.getUserById(id);
        if (rep == null) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        return toResponse(rep, manageableRolesOf(id));
    }

    private UserResponse toResponse(Map<String, Object> rep, List<String> roller) {
        return new UserResponse(
                stringId(rep),
                stringValue(rep, "username"),
                stringValue(rep, "firstName"),
                stringValue(rep, "lastName"),
                stringValue(rep, "email"),
                KeycloakAdminClient.firstAttribute(rep, ATTR_TELEFON),
                roller,
                boolValue(rep, "enabled"));
    }

    private static void putAttr(Map<String, Object> attrs, String key, String value) {
        if (hasText(value)) {
            attrs.put(key, List.of(value));
        } else {
            attrs.remove(key);
        }
    }

    private static String stringId(Map<String, Object> rep) {
        return stringValue(rep, "id");
    }

    private static String stringValue(Map<String, Object> rep, String key) {
        Object v = rep.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean boolValue(Map<String, Object> rep, String key) {
        Object v = rep.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
