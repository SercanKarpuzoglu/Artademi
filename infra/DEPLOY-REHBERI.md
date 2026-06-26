# Artademi — Üretim Deploy Rehberi (Hetzner + Docker + Caddy)

Sunucu: Helsinki, IP `37.27.241.117` · Domain: app.artademi.com (web+API), auth.artademi.com (Keycloak)

Bu rehber "tane tane" ilerler. Her bölümü yap, sonucu doğrula, sonrakine geç.

---

## 0. ÖN HAZIRLIK (kendi makinende, deploy ÖNCESİ)

### 0a. Bu paketteki dosyaları repoya yerleştir
Bu pakette verilen dosyaları repo'daki yerlerine koy:
- `backend.Dockerfile`     → `backend/Dockerfile`
- `web.Dockerfile`         → `web/Dockerfile`
- `nginx-spa.conf`         → `web/nginx-spa.conf`
- `docker-compose.prod.yml`→ `infra/docker-compose.prod.yml`
- `Caddyfile`              → `infra/Caddyfile`
- `.env.prod.example`      → `infra/.env.prod.example`

`.gitignore`'a ekle (sırlar git'e GİRMESİN):
```
infra/.env.prod
```

Commit + push et (Dockerfile'lar ve prod compose repoda olmalı, sunucu bunları clone'layacak):
```
git add backend/Dockerfile web/Dockerfile web/nginx-spa.conf infra/docker-compose.prod.yml infra/Caddyfile infra/.env.prod.example .gitignore
git commit -m "feat(infra): production deploy yapılandırması (compose.prod + Dockerfile + Caddy)"
git push origin main
```

### 0b. Keycloak realm export (yerelden — prod'a taşımak için)
Yerel Keycloak ÇALIŞIRKEN, realm'i JSON'a dök:
```
# infra/ içinde, yerel ortam ayakta:
docker exec artademi-keycloak /opt/keycloak/bin/kc.sh export \
  --realm Artademi --file /tmp/artademi-realm.json --users realm_file
docker cp artademi-keycloak:/tmp/artademi-realm.json ./artademi-realm.json
```
⚠️ Bu dosya kullanıcıları da içerir (test kullanıcıları dahil). Prod'a SADECE realm yapısını (client/rol/mapper/tema ayarı) taşımak istiyorsan, import sonrası test kullanıcılarını prod'da silebilirsin — ya da export'u `--users skip` ile alıp kullanıcıları prod'da sıfırdan oluşturursun. KARAR: temiz prod için `--users skip` öneririm (aşağıda not).

`artademi-realm.json`'u sunucuya kopyalayacağız (Bölüm 3).

---

## 1. SUNUCUDA KOD (git clone)

Sunucuda (ssh root@37.27.241.117):
```
# Git kurulu mu?
apt install -y git

# Repoyu çek (private repo → GitHub erişimi gerek, aşağıda not)
cd /opt
git clone https://github.com/SercanKarpuzoglu/Artademi.git artademi
cd artademi
```

⚠️ **Private repo erişimi:** clone sırasında GitHub kullanıcı adı + **Personal Access Token** (parola değil) sorar.
GitHub → Settings → Developer settings → Personal access tokens → "repo" yetkili token üret, parola yerine onu gir.
(Alternatif: sunucuda deploy SSH key üretip GitHub'a deploy key olarak ekle — daha temiz ama bir adım fazla.)

---

## 2. SIRLARI GİR (.env.prod)

Sunucuda:
```
cd /opt/artademi/infra
cp .env.prod.example .env.prod

# Güçlü şifreler üret (her biri için ayrı çalıştır, çıktıları kopyala):
openssl rand -base64 24    # DB şifresi için
openssl rand -base64 24    # Keycloak admin şifresi için

nano .env.prod   # alanları doldur, Ctrl+O kaydet, Ctrl+X çık
```
- `POSTGRES_PASSWORD` → ürettiğin güçlü şifre
- `KEYCLOAK_ADMIN_PASSWORD` → ürettiğin güçlü şifre
- `KEYCLOAK_ADMIN_CLIENT_SECRET` → ŞİMDİLİK boş/placeholder bırak; realm import sonrası dolduracağız (Bölüm 5).

⚠️ `.env.prod` dosyasını kimseye gösterme, git'e ekleme.

---

## 3. KEYCLOAK REALM'İNİ SUNUCUYA KOPYALA

Kendi makinende (Bölüm 0b'de ürettiğin dosya):
```
scp ./artademi-realm.json root@37.27.241.117:/opt/artademi/infra/
```

---

## 4. POSTGRES + KEYCLOAK'I AYAĞA KALDIR (önce auth katmanı)

Sunucuda, infra/ içinde:
```
cd /opt/artademi/infra
# Önce sadece DB + Keycloak (realm import için Keycloak lazım)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d postgres keycloak
docker compose -f docker-compose.prod.yml logs -f keycloak
```
Keycloak "started" diyene kadar bekle (ilk açılışta DB şema kurar, 1-2 dk). Ctrl+C ile log'dan çık (servis durmaz).

⚠️ Keycloak prod modда (`start --optimized`) bazen `--optimized` öncesi bir `build` ister. Hata alırsan, compose'da geçici olarak `command: ["start"]` (--optimized'sız) dene; çalışınca optimize edilebilir.

---

## 5. REALM IMPORT + BACKEND CLIENT SECRET

### 5a. Realm'i import et
```
docker cp ./artademi-realm.json artademi-keycloak:/tmp/artademi-realm.json
docker exec artademi-keycloak /opt/keycloak/bin/kc.sh import --file /tmp/artademi-realm.json
docker compose -f docker-compose.prod.yml restart keycloak
```

### 5b. Keycloak Console'a gir, prod ayarlarını yap
- Tarayıcı: https://auth.artademi.com (SSL birazdan Caddy ile gelecek — bu adımı Bölüm 6'dan SONRA da yapabilirsin)
- admin / (KEYCLOAK_ADMIN_PASSWORD)
- Realm `Artademi` → Clients → `artademi-app`:
  - Valid redirect URIs: `https://app.artademi.com/*`
  - Web origins: `https://app.artademi.com`
- Clients → `artademi-backend` → Credentials → secret'i KOPYALA
  → `.env.prod` içindeki `KEYCLOAK_ADMIN_CLIENT_SECRET`'e yapıştır
- (Login teması zaten realm export'ta geldiyse Themes'te `artademi` seçili olmalı; değilse seç.)

---

## 6. TÜM SİSTEMİ AYAĞA KALDIR (build dahil)

Sunucuda:
```
cd /opt/artademi/infra
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```
- Backend (Maven) + web (Vite) build edilir — ilk seferde birkaç dakika.
- Caddy 80/443'ü alır, app + auth için SSL sertifikası çeker (DNS doğru + port açık olduğu için otomatik).

Durumu izle:
```
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f caddy   # SSL alındı mı gör
```

---

## 7. CANLI TEST

- https://app.artademi.com → Keycloak login (temalı) → admin.a vb. ile giriş → dashboard
- https://auth.artademi.com → Keycloak (gerekirse admin console)
- SSL kilidi yeşil mi (Caddy sertifikası)

⚠️ **.env.prod backend secret güncellendiyse** backend'i yeniden başlat:
```
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

---

## SORUN GİDERME
- **SSL alınamıyor:** DNS gri bulut (DNS only) mu? 80/443 açık mı (ufw)? `docker compose logs caddy`.
- **Keycloak redirect hatası:** Client redirect URI / web origins prod adresi mi (Bölüm 5b).
- **Backend 500 / token hatası:** KEYCLOAK_ISSUER_URI = https://auth.artademi.com/realms/Artademi mi? Secret doğru mu?
- **Build OOM (bellek):** CX33 8GB yeter ama eşzamanlı build zorlarsa servisleri tek tek build et (`up -d --build backend`, sonra `web`).

---

## GÜNCEL DURUM (2026-06 — CANLI) ✅

**Yayında:** app.artademi.com (web + /api), auth.artademi.com (Keycloak), **artademi.com + www** (landing; Caddy `file_server`, www→apex 301). Hepsi SSL'li (Caddy/Let's Encrypt, Cloudflare DNS-only). Prod DB **Flyway v16**. Tek tenant **Lina Sanat Merkezi** (AKTIF) + super.admin; test tenant'ları (test/test2/Tab Sanat) kalıcı silindi (psql+kcadm).

### Platform 403 zinciri — ÇÖZÜLDÜ (eskiden "açık debug" idi; artık geçersiz)
Platform konsolundan kurum/kullanıcı oluşturma 403 veriyordu; **üç ayrı kök neden** bulundu ve çözüldü:
1. **Security 403** — çalışan backend imajı eski commit'ten build'di (`PlatformController` jar'da yoktu). → `docker compose -f docker-compose.prod.yml build --no-cache backend` + `up -d`.
2. **Provisioning 403** — Keycloak service-account `service-account-artademi-backend`'de realm-management rolleri (`manage-users`/`view-users`/`view-realm`) eksikti (realm import getirmemişti). → `kcadm` ile eklendi; **`infra/artademi-realm.json` export'una işlendi** (bir dahaki import getirir). (Keycloak admin parolası container env `KC_BOOTSTRAP_ADMIN_PASSWORD`.)
3. **CORS 403 "Invalid CORS request"** — backend CORS yalnız localhost'a izin veriyordu. Tek-domain'de tarayıcı same-origin POST'ta bile `Origin` header gönderir → reddediliyordu (GET/curl Origin göndermediği için geçiyordu). → CORS env-driven (`APP_CORS_ALLOWED_ORIGINS=https://app.artademi.com,https://artademi.com`), compose backend env'inde. Commit `61e4a1a`.

> ⚠️ "No static resource api/…" (500) = **eski/yarım derlenmiş kod** sinyali. Çözüm temiz build: `up -d --build backend` (yerelde `./mvnw clean compile`).

### Deploy hatırlatma (migration'lı sürüm)
```
cd /opt/artademi && git pull && cd infra
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build backend web
```
Flyway başlangıçta V15/V16'yı uygular (öğretmen hakediş göçü + grup hakediş_tipi). Keycloak/postgres/caddy'ye dokunma. `.env.prod` git'te DEĞİL.

### Kalan açık işler (prod)
- **SMTP yok** → forgot-password sayfası temalı ama mail göndermez; provisioning maili yok (info@artademi.com / Zoho bekliyor).
- **Ödeme entegrasyonu yok** (PayTR/iyzico) — subscription `paymentStatus` elle/`markPaid`.
