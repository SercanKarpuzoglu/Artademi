---
name: keycloak-auth
description: How authentication and authorization work — Keycloak as the identity provider with a single realm, a tenant_id claim plus role claims in the JWT, and the Spring Boot backend acting as a resource server that validates tokens and maps claims to tenant context and authorities. Consult this when touching login, security config, roles, or protected endpoints.
---

# Kimlik Doğrulama ve Yetki — Keycloak

## Model: Tek realm + tenant claim
- Tüm okullar (tenant) **tek bir Keycloak realm**'i paylaşır.
- Her kullanıcının token'ında iki kritik bilgi taşınır:
  - `tenant_id` — kullanıcının ait olduğu okul. Tenant izolasyonunun kaynağı (bkz. `multi-tenancy`).
  - roller — kullanıcının yetki seviyesi.
- `tenant_id` Keycloak'ta kullanıcı özniteliği (attribute) olarak tutulur ve bir protocol mapper ile token'a claim olarak eklenir.
- Okul sayısı çok büyürse realm-per-tenant'a geçiş bir seçenektir; başlangıç ve orta ölçek için tek realm yönetilebilir olandır.

## Roller
Tenant düzeyi roller (her okul kendi kullanıcıları için):
- `ADMIN` — okulun her şeyine erişir, ilk verileri (aidat, sınıf, kullanıcı atama) girer.
- `FRONTDESK` — sınıf/grup/öğrenci/yoklama. Para tarafı YOK.
- `FRONTDESK_ACCOUNTING` — yukarısı + günlük kasa + ön büro giderleri. Maaş/yönetim giderleri ve aylık raporlar YOK.
- `TEACHER` — yalnızca yoklama alma.

Platform düzeyi rol (senin için, tenant'ların üstünde):
- `SUPER_ADMIN` — tenant açar/askıya alır, abonelikleri yönetir. Tenant verisine iş amaçlı girmez (bkz. `subscription-billing`).

Not: Rol kaba yetkiyi verir. "Sadece kendi girdiğini düzeltebilir", "sadece ön büro giderlerini görür" gibi ince kurallar Keycloak'ta değil, backend'de (servis/method security) uygulanır.

## Backend = Resource Server
- Spring Boot, OAuth2 Resource Server olarak yapılandırılır; gelen `Authorization: Bearer <jwt>`'yi Keycloak'ın imzasıyla (JWKS) doğrular.
- Bir `JwtAuthenticationConverter`:
  - token'daki rolleri Spring `GrantedAuthority`'lere çevirir (`ROLE_ADMIN` vb.),
  - `tenant_id` claim'ini okuyup `TenantContext`'e koyar (multi-tenancy filtresi bunu kullanır).
- Endpoint'ler varsayılan **kapalı**; açıkça izin verilenler dışında kimlik ister. Yetki `@PreAuthorize`/SecurityFilterChain ile rol bazında kısıtlanır.
- Token süresi dolunca `401` + `error.code = "TOKEN_EXPIRED"` (bkz. `api-contract`).

## Demir kurallar
- Token/parola/secret kodda veya URL'de taşınmaz; Keycloak istemci secret'ı ortam değişkeninden okunur.
- `tenant_id`'ye istemci karar veremez; her zaman token'dan gelir.
- Frontend (web + mobil) Keycloak ile OIDC akışıyla giriş yapar; access token'ı güvenli şekilde saklar (mobilde `expo-secure-store`), her isteğe Bearer olarak ekler.
