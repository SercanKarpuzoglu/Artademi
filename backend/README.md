# Artademi Backend

Çok kiracılı (multi-tenant) SaaS backend — **iskelet aşaması**.
Spring Boot 3.3 · Java 21 · Maven · PostgreSQL · Flyway.

> Bu aşamada tenant izolasyonu ve auth (Keycloak / OAuth2) **henüz yok**;
> sadece çalışan bir temel + Postgres bağlantısı vardır.

## Gereksinimler

- Java 21
- Çalışan PostgreSQL (yerelde `infra/` altındaki docker-compose ile):
  ```bash
  cd ../infra && docker compose up -d postgres
  ```
  Bu, `localhost:5432` üzerinde `artademi` veritabanını ayağa kaldırır.

## Ortam değişkenleri

Bağlantı bilgileri ortam değişkeninden okunur; **kodda sabit parola yoktur**.
Tüm yerel değerler tek dosyada toplanır: **`backend/.env`** (git'e girmez).

İlk kurulumda örnekten kopyalayın ve değerleri doldurun:

```bash
cp .env.example .env   # sonra .env içindeki parolayı düzenleyin
```

| Değişken                     | Varsayılan                                       | Açıklama                          |
|------------------------------|--------------------------------------------------|-----------------------------------|
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5433/artademi`      | JDBC URL (yerel port 5433)        |
| `SPRING_DATASOURCE_USERNAME` | `artademi`                                        | DB kullanıcısı                    |
| `SPRING_DATASOURCE_PASSWORD` | _(yok — zorunlu)_                                | DB parolası                       |
| `SERVER_PORT`                | `8081`                                            | HTTP portu (Keycloak 8080'de)     |

`SPRING_DATASOURCE_PASSWORD`, `infra/.env` içindeki `POSTGRES_PASSWORD` ile aynı olmalıdır.

## Çalıştırma

`.env` dosyasındaki değişkenleri kabuğa yükleyip tek komutla başlatın:

```bash
set -a && source .env && set +a && ./mvnw spring-boot:run
```

- `set -a`: bundan sonra atanan/source edilen değişkenleri otomatik **export** eder.
- `source .env`: `backend/.env` içindeki değerleri kabuğa yükler.
- `set +a`: otomatik export'u kapatır.
- `./mvnw spring-boot:run`: Spring Boot bu env değişkenlerini okuyarak başlar.

Açılışta Flyway `V1__init.sql` migration'ını uygular. Uygulama `http://localhost:8081`
üzerinde çalışır.

## Derleme / test

```bash
./mvnw -q compile   # derleme
./mvnw test         # testler (Testcontainers için Docker gerekir)
```

## Health

Yalnızca health endpoint açıktır:

```
GET http://localhost:8081/actuator/health
```

## Paket düzeni

```
com.artademi
├── BackendApplication      # giriş noktası
├── common                  # ApiResponse, GlobalExceptionHandler, TenantContext, tenant filtresi (gelecek)
└── platform                # tenant / subscription / plan — SUPER_ADMIN (gelecek)
```
