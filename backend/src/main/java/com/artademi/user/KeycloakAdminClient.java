package com.artademi.user;

import com.artademi.common.exception.ConflictException;
import com.artademi.common.exception.NotFoundException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Keycloak Admin REST API istemcisi (spring-web {@link RestClient}; keycloak-admin-client / webflux
 * KULLANILMAZ). Service account ({@code client_credentials}) ile token alir, bellekte cache'ler ve
 * suresi dolmadan once yeniler.
 *
 * <p>Bu sinif SADECE Keycloak protokolunu konusur (tenant/rol is kurallari yok); izolasyon ve rol
 * mantigi {@link UserService}'tedir. UserRepresentation'lar tip-baginsiz {@code Map<String,Object>}
 * olarak gecer; attribute'lar KC'de dizi ({@code {"tenant_id":["<uuid>"]}}) oldugundan ilk degeri
 * okuyan/yazan yardimcilar saglanir.
 */
@Service
public class KeycloakAdminClient {

    /** Token, kalan suresi bu esigin altina dustugunde yenilenir. */
    private static final long REFRESH_SKEW_SECONDS = 30L;

    private final RestClient rest;
    private final KeycloakProperties props;

    /** Bellekteki cache (synchronized getter ile korunur). */
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(RestClient keycloakRestClient, KeycloakProperties props) {
        this.rest = keycloakRestClient;
        this.props = props;
    }

    // ---------------------------------------------------------------------
    // Token yonetimi
    // ---------------------------------------------------------------------

    /**
     * Gecerli service-account access token'ini doner; cache bos veya 30 sn'den az kalmissa yeniler.
     * Dev icin synchronized yeterli izolasyon saglar.
     */
    synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.adminClientId());
        form.add("client_secret", props.adminClientSecret());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.post()
                .uri(props.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("Keycloak service-account token alinamadi");
        }
        this.cachedToken = String.valueOf(body.get("access_token"));
        long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60L;
        this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    private RestClient.RequestHeadersSpec<?> authGet(String uri) {
        return rest.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken());
    }

    // ---------------------------------------------------------------------
    // Kullanici islemleri
    // ---------------------------------------------------------------------

    /**
     * Tenant'a gore kullanici arar. {@code q=tenant_id:{tenantId}} attribute filtresidir;
     * {@code search} kullanici adi/ad/email uzerinde serbest metindir.
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> searchUsers(String search, Boolean enabled, int first, int max, String tenantId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(props.adminBasePath() + "/users")
                .queryParam("q", "tenant_id:" + tenantId)
                .queryParam("first", first)
                .queryParam("max", max);
        if (StringUtils.hasText(search)) {
            b.queryParam("search", search);
        }
        if (enabled != null) {
            b.queryParam("enabled", enabled);
        }
        List<Map<String, Object>> result = authGet(b.build().toUriString())
                .retrieve()
                .body(List.class);
        return result == null ? List.of() : result;
    }

    /** Tek kullaniciyi (attribute'lariyla) doner; 404 ise null. */
    Map<String, Object> getUserById(String id) {
        return getMapOrNull(props.adminBasePath() + "/users/" + id);
    }

    /**
     * Verilen kullanici adi realm'de (tum tenant'lar genelinde) zaten var mi? Tenant'tan BAGIMSIZ
     * genel bir kontroldur (username Keycloak'ta realm-genelinde tekildir); bu yuzden provisioning'de
     * tenant'lar arasi username cakismasi yakalanir. {@code exact=true} ile tam eslesme aranir.
     */
    @SuppressWarnings("unchecked")
    boolean usernameExists(String username) {
        String uri = UriComponentsBuilder.fromUriString(props.adminBasePath() + "/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .queryParam("max", 1)
                .build()
                .toUriString();
        List<Map<String, Object>> result = authGet(uri).retrieve().body(List.class);
        return result != null && !result.isEmpty();
    }

    /**
     * GET ile tek bir JSON nesnesi okur; 404'te (error body parse etmeden) null doner. Diger
     * hatalar yukari firlatilir.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapOrNull(String uri) {
        return authGet(uri).exchange((request, response) -> {
            if (response.getStatusCode().value() == 404) {
                return null;
            }
            if (response.getStatusCode().isError()) {
                throw new IllegalStateException(
                        "Keycloak hata yaniti: " + response.getStatusCode());
            }
            return response.bodyTo(Map.class);
        });
    }

    /**
     * Kullanici olusturur. 201'de Location header'indaki yeni id doner. KC 409 (yinelenen
     * kullanici adi/email) -> {@link ConflictException}.
     */
    String createUser(Map<String, Object> rep) {
        URI location = rest.post()
                .uri(props.adminBasePath() + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(rep)
                .retrieve()
                .onStatus(s -> s.value() == 409, (req, res) -> {
                    throw new ConflictException("Kullanıcı adı veya e-posta zaten kayıtlı");
                })
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        if (location == null) {
            throw new IllegalStateException("Keycloak kullanici olusturma yaniti Location icermiyor");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Kullaniciyi gunceller (PUT, tam temsil). */
    /**
     * Kullaniciyi gunceller. ONEMLI: Keycloak PUT /users/{id} KISMI DEGIL, gonderilen temsili
     * ESAS alir; firstName/lastName/email gibi alanlar gonderilmezse SILINIR (null'lanir). Profil
     * silinince VERIFY_PROFILE required-action devreye girip giris "Account is not fully set up" ile
     * bloke olur. Bu yuzden burada mevcut tam temsil cekilip uzerine {@code changes} bindirilir
     * (merge); boylece {@code setEnabled}/parola-degisimi gibi kismi guncellemeler profili korur.
     */
    void updateUser(String id, Map<String, Object> changes) {
        Map<String, Object> current = getUserById(id);
        if (current == null) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
        Map<String, Object> merged = new HashMap<>(current);
        merged.putAll(changes);
        rest.put()
                .uri(props.adminBasePath() + "/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(merged)
                .retrieve()
                .onStatus(s -> s.value() == 409, (req, res) -> {
                    throw new ConflictException("Kullanıcı adı veya e-posta zaten kayıtlı");
                })
                .toBodilessEntity();
    }

    /** Aktif/pasif degisikligi (PUT {enabled}). */
    void setEnabled(String id, boolean enabled) {
        updateUser(id, Map.of("enabled", enabled));
    }

    void deleteUser(String id) {
        rest.delete()
                .uri(props.adminBasePath() + "/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .toBodilessEntity();
    }

    /** Parola sifirlar; {@code temporary=false} -> KC required-action olusturmaz. */
    void resetPassword(String id, String newPassword, boolean temporary) {
        Map<String, Object> body = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", temporary);
        rest.put()
                .uri(props.adminBasePath() + "/users/" + id + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------------------------------------------------------------
    // Realm rol islemleri
    // ---------------------------------------------------------------------

    /** Realm rolunu ({id,name}) doner; yoksa null. */
    Map<String, Object> getRealmRole(String name) {
        return getMapOrNull(props.adminBasePath() + "/roles/" + name);
    }

    /** Kullaniciya atanmis realm rollerini doner. */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> getUserRealmRoles(String id) {
        List<Map<String, Object>> roles = authGet(
                props.adminBasePath() + "/users/" + id + "/role-mappings/realm")
                .retrieve()
                .body(List.class);
        return roles == null ? List.of() : roles;
    }

    /** Verilen realm rollerini ([{id,name}...]) kullaniciya ekler. */
    void addRealmRoles(String id, List<Map<String, Object>> roles) {
        if (roles.isEmpty()) {
            return;
        }
        rest.post()
                .uri(props.adminBasePath() + "/users/" + id + "/role-mappings/realm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity();
    }

    /** Verilen realm rollerini ([{id,name}...]) kullanicidan kaldirir (DELETE + body). */
    void removeRealmRoles(String id, List<Map<String, Object>> roles) {
        if (roles.isEmpty()) {
            return;
        }
        rest.method(org.springframework.http.HttpMethod.DELETE)
                .uri(props.adminBasePath() + "/users/" + id + "/role-mappings/realm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity();
    }

    // ---------------------------------------------------------------------
    // Parola dogrulama (Direct Access Grants — public client)
    // ---------------------------------------------------------------------

    /**
     * Kullanicinin mevcut parolasini dogrular: public client uzerinden password grant denenir.
     * 200 -> true, 401 -> false. Diger hatalar yukari firlatilir.
     */
    boolean verifyPassword(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.appClientId());
        form.add("username", username);
        form.add("password", password);

        boolean[] unauthorized = {false};
        rest.post()
                .uri(props.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(s -> s.value() == 401 || s.value() == 400, (req, res) -> unauthorized[0] = true)
                .toBodilessEntity();
        return !unauthorized[0];
    }

    // ---------------------------------------------------------------------
    // Attribute yardimcilari (KC attribute degerleri dizidir)
    // ---------------------------------------------------------------------

    /** Bir UserRepresentation'in {@code attributes} haritasindan ilk degeri okur (yoksa null). */
    @SuppressWarnings("unchecked")
    static String firstAttribute(Map<String, Object> rep, String key) {
        if (rep == null) {
            return null;
        }
        Object attrs = rep.get("attributes");
        if (!(attrs instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = ((Map<String, Object>) map).get(key);
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : String.valueOf(first);
        }
        if (value != null) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * Bir attribute'u (dizi olarak) verilen rep'in {@code attributes} haritasina yazar; mevcut
     * diger attribute'lar korunur. {@code value} null/blank ise attribute kaldirilir.
     */
    @SuppressWarnings("unchecked")
    static void putAttribute(Map<String, Object> rep, String key, String value) {
        Map<String, Object> attrs = (Map<String, Object>) rep.get("attributes");
        if (attrs == null) {
            attrs = new LinkedHashMap<>();
            rep.put("attributes", attrs);
        }
        if (StringUtils.hasText(value)) {
            attrs.put(key, List.of(value));
        } else {
            attrs.remove(key);
        }
    }

    /** Mevcut rep'in attribute haritasinin degistirilebilir bir kopyasini doner (null-guvenli). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> copyAttributes(Map<String, Object> rep) {
        Object attrs = rep == null ? null : rep.get("attributes");
        if (attrs instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    /** Rol-mapping listesinden rol adlarini ayiklar. */
    static List<String> roleNames(List<Map<String, Object>> roleMappings) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> r : roleMappings) {
            Object n = r.get("name");
            if (n != null) {
                names.add(String.valueOf(n));
            }
        }
        return names;
    }
}
