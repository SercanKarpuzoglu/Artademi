---
name: spring-boot-backend
description: Backend patterns for the multi-tenant Spring Boot service — entity (with tenant_id), repository, service, controller, DTO mapping, validation, exception handling, Flyway migrations, and how tenant isolation and Keycloak auth are enforced. Consult this when adding or changing any backend code.
---

# Spring Boot Backend Konvansiyonları (Çok Kiracılı)

## Katmanlar ve sorumluluklar
- **Controller** — ince. HTTP'yi alır, DTO doğrular, servisi çağırır, `ApiResponse` döndürür. İş kuralı YOK. **Tenant'ı parametre olarak ASLA kabul etmez.**
- **Service** — tüm iş kuralı + `@Transactional`. Entity ⇄ DTO. Tenant'ı `TenantContext`'ten alır (bkz. `multi-tenancy`).
- **Repository** — `JpaRepository`. Global tenant filtresi aktif olduğundan iş sorguları otomatik tenant kapsamında çalışır.
- **Entity** — JPA `@Entity`; iş entity'leri `tenant_id` taşır (tercihen ortak `TenantAware` taban sınıf). Dışarıya hep DTO döner.
- **DTO** — `dto/` altında; istek DTO'su `@Valid`.

## Çok kiracılılık (zorunlu)
- Tüm iş entity'leri `tenant_id` taşır ve global Hibernate tenant filtresine tabidir.
- `tenant_id` yazma sırasında `TenantContext`'ten otomatik set edilir; geliştirici elle vermez.
- Tenant `TenantContext`'e bir security filtresinde JWT'nin `tenant_id` claim'inden konur, istek bitince temizlenir.
- `platform/` paketindeki tablolar (tenant, subscription, plan) tenant filtresinden MUAFtır ve yalnızca `SUPER_ADMIN`'e açıktır.
- Detaylar: `multi-tenancy` ve `keycloak-auth` skilleri.

## Güvenlik
- OAuth2 Resource Server; gelen JWT Keycloak imzasıyla doğrulanır. Roller `GrantedAuthority`'ye, `tenant_id` `TenantContext`'e map'lenir.
- Endpoint'ler varsayılan kapalı; yetki `@PreAuthorize`/SecurityFilterChain ile rol bazında. İnce kurallar ("sadece kendi girdiğini düzeltir") serviste enforce edilir.
- Sır/anahtar/parola kodda değil, ortam değişkeninde. Kullanıcı girdisi parametreli sorgu ile.

## Bir özellik eklerken iskelet (örnek: Student)
```
student/
  Student.java               # @Entity, TenantAware (tenant_id)
  StudentRepository.java
  StudentService.java        # @Service, @Transactional, TenantContext
  StudentController.java      # @RestController @RequestMapping("/api/students")
  dto/CreateStudentRequest.java / StudentResponse.java / StudentMapper.java
```

## Doğrulama & hata yönetimi
- Bean Validation; controller `@Valid`. Tek `@RestControllerAdvice GlobalExceptionHandler`.
- `NotFoundException`→404, `ConflictException`→409, doğrulama→400, `TENANT_SUSPENDED`→403, `TOKEN_EXPIRED`→401. Ham stacktrace sızmaz.

## ApiResponse
- Tüm dönüşler `ApiResponse<T>`; listede `PageMeta`. Bkz. `api-contract`.

## Flyway migration
- Şema değişikliği = yeni dosya (`V<n>__<açıklama>.sql`); mevcut migration düzenlenmez.
- İş tabloları `tenant_id NOT NULL` + indeksli (bileşik indekslerin ilk kolonu genelde `tenant_id`).
- `ddl-auto=validate`. Yıkıcı değişiklikte önce uyar.

## Test
- Servis birim testi (Mockito) + entegrasyon testi Testcontainers (gerçek PostgreSQL).
- **Her özellikte tenant izolasyon testi:** A tenant'ının verisi, B tenant bağlamında istek atılınca görünmemeli. Bkz. `testing-standards`.
