---
name: api-contract
description: The shared REST API contract every endpoint follows — response envelope, pagination, error shape, HTTP status codes, resource naming, and auth. Consult this whenever creating or consuming an endpoint on backend, web, or mobile so all three stay in sync.
---

# API Sözleşmesi

Backend bu sözleşmeyi üretir; web ve mobil aynısını tüketir. Sapma olmaz.

## Yanıt zarfı (envelope)
Tüm başarılı yanıtlar aynı şekle sahiptir:
```json
{ "success": true, "data": { ... }, "error": null, "meta": null }
```
Liste/sayfalı yanıtlar `meta` ile döner:
```json
{
  "success": true,
  "data": [ ... ],
  "error": null,
  "meta": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
}
```

## Hata şekli
```json
{
  "success": false,
  "data": null,
  "error": { "code": "VALIDATION_ERROR", "message": "Ad zorunludur", "fields": { "name": "boş olamaz" } },
  "meta": null
}
```
`code` makine-okur sabit bir dize (ör. `NOT_FOUND`, `VALIDATION_ERROR`, `UNAUTHORIZED`, `CONFLICT`, `INTERNAL`). `message` kullanıcıya gösterilebilir Türkçe metin. `fields` opsiyonel, alan bazlı doğrulama hataları.

## HTTP durum kodları
- `200` okuma/güncelleme başarılı · `201` oluşturma başarılı · `204` silme başarılı (gövde yok)
- `400` doğrulama hatası · `401` kimlik yok/geçersiz · `403` yetki yok · `404` bulunamadı · `409` çakışma (ör. tekrarlı kayıt) · `500` sunucu hatası

## Kaynak isimlendirme
- Çoğul, kebab-case: `/api/students`, `/api/lesson-sessions`, `/api/teachers/{id}/payouts`
- Filtre/sayfa query ile: `/api/students?status=ACTIVE&page=0&size=20`
- Eylemler kaynak altında: `POST /api/lessons/{id}/attendance`

## Kimlik doğrulama
- `Authorization: Bearer <jwt>` başlığı. Token gövdede veya URL'de ASLA taşınmaz.
- Token süresi dolduğunda `401` + `error.code = "TOKEN_EXPIRED"`.

## Frontend tarafı
- Web ve mobilde Axios interceptor zarfı açar: `res.data.data` döndürür, `success:false` ise `error`'ı fırlatır.
- Tipler tek yerde tanımlanır (`shared/` veya her iki tarafta aynalanır) ki backend değişince iki uç da görür.
