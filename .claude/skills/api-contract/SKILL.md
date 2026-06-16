---
name: api-contract
description: The shared REST API contract every endpoint follows — response envelope, pagination, error shape, HTTP status codes, resource naming, and auth (Keycloak JWT with tenant_id claim). Consult this whenever creating or consuming an endpoint on backend, web, or mobile so all three stay in sync.
---

# API Sözleşmesi

Backend bu sözleşmeyi üretir; web ve mobil aynısını tüketir. Sapma olmaz.

## Yanıt zarfı (envelope)
Başarılı:
```json
{ "success": true, "data": { ... }, "error": null, "meta": null }
```
Sayfalı liste:
```json
{ "success": true, "data": [ ... ], "error": null,
  "meta": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 } }
```

## Hata şekli
```json
{ "success": false, "data": null,
  "error": { "code": "VALIDATION_ERROR", "message": "Ad zorunludur", "fields": { "name": "boş olamaz" } },
  "meta": null }
```
`code` makine-okur sabit (`NOT_FOUND`, `VALIDATION_ERROR`, `UNAUTHORIZED`, `CONFLICT`, `TOKEN_EXPIRED`, `TENANT_SUSPENDED`, `INTERNAL`). `message` kullanıcıya gösterilebilir Türkçe.

## HTTP durum kodları
- `200` ok · `201` oluşturma · `204` silme · `400` doğrulama · `401` kimlik · `403` yetki/tenant askıda · `404` bulunamadı · `409` çakışma · `500` sunucu.

## Kaynak isimlendirme
- Çoğul, kebab-case: `/api/students`, `/api/lesson-sessions`, `/api/teachers/{id}/payouts`.
- Filtre/sayfa query ile: `/api/students?status=ACTIVE&page=0&size=20`.

## Kimlik doğrulama ve TENANT
- `Authorization: Bearer <jwt>` (Keycloak). Token gövdede/URL'de ASLA taşınmaz.
- **Tenant kimliği yalnızca JWT'deki `tenant_id` claim'inden gelir.** URL'de, body'de veya query'de `tenant_id` ASLA gönderilmez/kabul edilmez — sahtelenebilir. Bkz. `multi-tenancy`, `keycloak-auth`.
- Token süresi dolunca `401` + `TOKEN_EXPIRED`. Tenant askıdaysa iş endpoint'leri `403` + `TENANT_SUSPENDED`.

## Frontend tarafı
- Web ve mobilde Axios interceptor: Bearer ekler, zarfı açar (`data.data`), `success:false` ise `error`'ı fırlatır.
- Tipler tek yerde (`shared/` veya aynalı) tanımlanır; backend değişince iki uç da güncellenir.
