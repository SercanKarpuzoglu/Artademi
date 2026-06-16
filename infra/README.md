# Artademi — Yerel Altyapı

Yerel geliştirme için **PostgreSQL 16** + **Keycloak 26** (Docker Compose).
Henüz backend kodu yok; bu sadece bağımlılıkları ayağa kaldırır.

## İlk kurulum

```bash
cd infra
cp .env.example .env      # değerleri kontrol et / güvenli parolalarla doldur
```

> `.env` git'e girmez (sırlar). `.env.example` ve `docker-compose.yml` git'e girer.

## Çalıştırma

```bash
cd infra
docker compose up -d        # arka planda başlat
docker compose ps           # durum
docker compose logs -f keycloak   # Keycloak loglarını izle
```

İlk başlatmada `postgres-init/` script'i `artademi` (uygulama) yanında
`keycloak` veritabanını da oluşturur. Keycloak, postgres healthcheck'i
geçene kadar bekler.

## Erişim

| Servis     | Adres                     | Giriş                                            |
|------------|---------------------------|--------------------------------------------------|
| Keycloak   | http://localhost:8080     | `.env` içindeki `KEYCLOAK_ADMIN` / `..._PASSWORD` |
| PostgreSQL | `localhost:5432`          | `.env` içindeki `POSTGRES_USER` / `..._PASSWORD`  |

PostgreSQL veritabanları: `artademi` (uygulama), `keycloak` (Keycloak).

## Durdurma

```bash
cd infra
docker compose down            # konteynerleri durdur (veri KALIR)
docker compose down -v         # veriyi de sil (postgres volume dahil) — DİKKAT
```

Veri `artademi-postgres-data` adlı Docker volume'unda kalıcıdır.
