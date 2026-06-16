---
name: project-architecture
description: The canonical structure, tech stack, and layering rules for every app we build. Consult this whenever creating a new project, deciding where a file goes, naming a package/folder, or wiring backend, web, and mobile together. Spring Boot + React (Vite) web + React Native (Expo) mobile in one repo.
---

# Proje Mimarisi

Her uygulama aynı düzende kurulur. Bu skill nereye ne konacağının tek doğru kaynağıdır.

## Teknoloji yığını (varsayılan)
- **Backend:** Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, PostgreSQL, Flyway (migration), Bean Validation, Spring Security (JWT).
- **Web:** React 18 + TypeScript, Vite, React Router, TanStack Query (sunucu durumu), Axios (API istemcisi), React Hook Form + Zod (formlar).
- **Mobil:** React Native + Expo + TypeScript, React Navigation, TanStack Query, aynı Axios tabanlı API istemcisi.
- Yığında değişiklik varsa (Gradle, Redux, Flutter vb.) bu skill güncellenir; geri kalan skiller buna bakar.

## Klasör düzeni (tek repo / monorepo)
```
<proje-adı>/
  backend/                 # Spring Boot uygulaması
    src/main/java/com/<şirket>/<proje>/
      <özellik>/            # özelliğe göre paket (feature-based), katmana göre DEĞİL
        <Özellik>Controller.java
        <Özellik>Service.java
        <Özellik>Repository.java
        <Özellik>.java        # JPA entity
        dto/                # request/response DTO'ları
      common/              # ortak: hata yönetimi, ApiResponse, config, security
    src/main/resources/db/migration/   # Flyway: V1__..., V2__...
    src/test/java/...
  web/                     # React + Vite
    src/
      api/                 # Axios client + endpoint fonksiyonları + tipler
      features/<özellik>/  # bileşenler + hook'lar, özelliğe göre
      components/          # paylaşılan/sunum bileşenleri
      lib/                 # yardımcılar, query client
      routes/
  mobile/                  # React Native + Expo
    src/
      api/                 # web ile AYNI sözleşme; tipleri paylaş/aynala
      features/<özellik>/
      navigation/
      components/
  shared/ (opsiyonel)      # web+mobil paylaşılan TS tipleri / zod şemaları
```

## Temel kurallar
1. **Özelliğe göre grupla (feature-first), katmana göre değil.** "öğrenci" özelliğiyle ilgili her şey aynı pakette/klasörde durur. Tüm controller'ları tek klasöre toplama.
2. **Tek API sözleşmesi.** Backend ne döndürürse web ve mobil aynı tipi tüketir. Bkz. `api-contract` skill.
3. **Mobil her zaman dahildir.** Yeni bir özellik bittiğinde backend + web + mobil üç tarafta da düşünülür. Mobil API istemcisi web'inkiyle aynı sözleşmeyi kullanır.
4. **İş kuralı sadece serviste.** Controller ince, repository veriye erişir, mantık serviste. Bkz. `spring-boot-backend`.
5. **Sırlar koda girmez.** API anahtarı, parola, token asla kaynak kodda/commit'te olmaz; `.env` / ortam değişkeni ve `application-*.yml` (git'e girmeyen) kullanılır.

## Yeni proje başlatma sırası
1. `backend/` iskeleti + `common/` (ApiResponse, GlobalExceptionHandler, security).
2. İlk Flyway migration (`V1__init.sql`).
3. `web/` iskeleti + `api/` client + query client.
4. `mobile/` iskeleti + aynı `api/` sözleşmesi.
5. İlk özelliği uçtan uca `feature-builder` agent'ı ile kur.
