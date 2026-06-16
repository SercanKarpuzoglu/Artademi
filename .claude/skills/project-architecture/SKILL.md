---
name: project-architecture
description: The canonical structure, tech stack, and layering rules for the Artademi SaaS. Multi-tenant Spring Boot backend + React (Vite) web + React Native (Expo) mobile, with Keycloak auth and a platform subscription layer. Consult this when creating a new project, deciding where a file goes, naming a package/folder, or wiring backend, web, and mobile together.
---

# Proje Mimarisi (Artademi — Çok Kiracılı SaaS)

Bu, birden çok sanat okuluna abonelikle satılan çok kiracılı (multi-tenant) bir SaaS'tır. İki katman vardır:
- **Platform katmanı** — tenant'lar, abonelikler, planlar, `SUPER_ADMIN`. Tenant'ların üstündedir. Bkz. `subscription-billing`.
- **Tenant katmanı** — her okulun kendi iş verisi (öğrenci, ders, yoklama, hakediş, kasa...). Her kaydı `tenant_id` taşır. Bkz. `multi-tenancy`.

## Teknoloji yığını
- **Backend:** Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, **Spring Security OAuth2 Resource Server (Keycloak)**.
- **Auth:** Keycloak, tek realm + `tenant_id` claim. Bkz. `keycloak-auth`.
- **Web:** React 18 + TypeScript, Vite, React Router, TanStack Query, Axios, React Hook Form + Zod, OIDC ile Keycloak girişi.
- **Mobil:** React Native + Expo + TypeScript (öğretmen/asistan, hızlı yoklama odaklı), aynı API sözleşmesi.
- **Ödeme (platform aboneliği):** iyzico/PayTR (Türkiye), `PaymentProvider` arkasında soyutlanır.
- **Hosting:** Hetzner (Docker), ayrı staging + production. Web/landing Netlify'da statik.

## Klasör düzeni (monorepo)
```
artademi/
  backend/
    src/main/java/com/artademi/
      platform/            # tenant, subscription, plan — SUPER_ADMIN; tenant filtresinden muaf
      <özellik>/           # tenant iş özelliği (feature-first): student, group, attendance...
        <Özellik>Controller / Service / Repository / Entity + dto/
      common/              # ApiResponse, GlobalExceptionHandler, security, TenantContext, tenant filtresi
    src/main/resources/db/migration/   # Flyway
  web/      src/ api/ features/ components/ lib/ routes/
  mobile/   src/ api/ features/ navigation/ components/
  shared/   # web+mobil paylaşılan TS tipleri / zod şemaları
```

## Temel kurallar
1. **Her iş tablosu `tenant_id` taşır; tenant her zaman JWT'den gelir, asla istemciden.** En önemli kural — bkz. `multi-tenancy`.
2. **Platform vs tenant ayrımı.** Abonelik/tenant yönetimi `platform/` altında ve `SUPER_ADMIN`'e özeldir; tenant filtresinden muaftır. Karıştırma.
3. **Özelliğe göre grupla** (feature-first), katmana göre değil.
4. **Tek API sözleşmesi**; backend ne döndürürse web ve mobil aynı tipi tüketir. Bkz. `api-contract`.
5. **Mobil her zaman dahildir** (özellikle yoklama). Web ile yakın tutulur.
6. **İş kuralı sadece serviste.** Controller ince, repository veriye erişir.
7. **Sırlar koda girmez** (Keycloak secret, ödeme anahtarı, DB parolası → ortam değişkeni).

## Yeni proje başlatma sırası
1. `backend/` + `common/` (ApiResponse, GlobalExceptionHandler, **TenantContext + global tenant filtresi**, Keycloak resource-server config).
2. `platform/` iskeleti (tenant, subscription, plan) + ilk Flyway migration (tenant tabloları + tenant_id'li iş tabloları).
3. Keycloak realm/client/rol + `tenant_id` mapper.
4. `web/` + `api/` + OIDC giriş; `mobile/` + aynı sözleşme.
5. İlk tenant iş özelliğini `feature-builder` ile uçtan uca kur.
