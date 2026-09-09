# Artademi — Proje Devir Dökümanı (Handoff)

> **Bu dosyanın amacı:** Backend + Web + Kullanıcı/Tenant yönetimi + **Platform fazı (SUPER_ADMIN tenant yönetimi)** tamamlandı. Yeni işe **temiz bir sohbet penceresinde** başlamak için tüm bağlamı tek yerde toplar.
> **Son güncelleme:** 2026-06 / **PROD CANLI** (app/auth/landing) + **iki feedback işi (grup transferi + Model C çoklu hakediş, V15+V16) + platform konsolu tam (kullanıcı CRUD + soft-delete) + CORS/provisioning/Security 403 zinciri çözüldü.** Daha önce: TEACHER /mine, Dashboard, Logo, Keycloak login teması, subscription (V14), platform fazı, backend çekirdeği, tüm web modülleri.
> **İletişim dili:** Türkçe. **Geliştirici:** Sercan (solo). **Çalışma stili:** "tane tane" — her modül gerçek test + curl ile doğrulanmadan bir sonrakine geçilmez.

---

## 1. Proje Nedir

**Artademi** = sanat okulları (dans / bale / müzik) için **çok-kiracılı (multi-tenant) yönetim SaaS'ı**. Aylık abonelikle birden çok okula satılacak. Her okul = bir tenant. Tenant kimliği UUID, JWT içindeki `tenant_id` claim'i ile taşınır.

**Çekirdek amaç:** finansal akış kontrolü + öğrenci/grup/yoklama takibi.

---

## 2. Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Maven, Hibernate, Flyway |
| DB | PostgreSQL 16 (Docker) |
| Auth | Keycloak 26 (tek realm + `tenant_id` claim) |
| Web (frontend) | React + Vite + TypeScript + Tailwind + keycloak-js + TanStack Query + React Hook Form + Zod |
| Mobil | (henüz yok — ileride React Native + Expo) |
| Altyapı | Docker, GitHub (private repo) |

**Repo:** `github.com/SercanKarpuzoglu/Artademi` (private)
**Proje kökü:** `/Users/sercankarpuzoglu/dev/Artademi`

---

## 3. Yerel Ortamı Ayağa Kaldırma (sıra önemli)

```bash
# 1) Container'lar (Postgres + Keycloak)
cd infra && docker compose up -d && docker compose ps
# Postgres dışarıya 5433 portunda (5432 DEĞİL — Homebrew çakışması), Keycloak 8080

# 2) Backend (port 8081)
cd ../backend
set -a && source .env && set +a
./mvnw spring-boot:run
# "Started BackendApplication" görünce hazır. DevTools aktif: kod değişince
# `./mvnw compile` ile otomatik restart olur (elle Ctrl+C gerekmez).

# 3) Web (port 5173)
cd ../web && npm run dev
```

**Önemli notlar:**
- **Docker Desktop açık olmalı** (testler Testcontainers kullanır).
- `backend/.env` git'te YOK. İçeriği: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/artademi`, user/pass `artademi/artademi_local_2026`, `SERVER_PORT=8081`, `KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/Artademi`.
- **DevTools:** Kod değişince backend kendini yeniler ama **derleme tetiklenmeli** — `./mvnw compile` yeter (sadece kaydetmek yetmez). "No static resource" hatası = eski kod çalışıyor işareti → derle/yeniden başlat.
- Test çalıştırmadan önce backend'i durdurmaya gerek yok (Testcontainers ayrı port), ama canlı curl için backend açık olmalı.

---

## 4. Keycloak (kurulu, hazır)

- **Realm:** `Artademi` (büyük A, case-sensitive)
- **Client:** `artademi-app` (public, Client auth OFF, Standard flow + Direct access grants açık, PKCE S256). Redirect: `localhost:5173/*` ve `localhost:8081/*`. Web origins: `*`.
- **5 realm rolü:** `ADMIN`, `FRONTDESK`, `FRONTDESK_ACCOUNTING`, `TEACHER`, `SUPER_ADMIN`
- ✅ **Login teması (YENİ):** `infra/keycloak-theme/artademi/` (mount → `/opt/keycloak/themes`). erik-ahududu + Fraunces/Manrope + tam logo. `parent=keycloak` + CSS (logo CSS background ile; `.ftl` override YOK → sürüm-sağlam). Realm `loginTheme=artademi`, `resetPasswordAllowed=true`. ⚠️ Forgot-password akışı AÇIK ama **SMTP YOK** — mail gitmez (ayrı iş). Tema değişince `docker compose restart keycloak`.
- **tenant_id claim:** `artademi-app-dedicated` scope'ta User Attribute mapper (Token Claim Name=`tenant_id`, access token'a eklenir). Keycloak 26'da kullanıcı attribute'ları Realm settings → User profile'dan **resmi alan** olarak tanımlı.

### Test Kullanıcıları (parola `Test1234!`, Temporary=Off)

| Kullanıcı | Rol | Tenant | Not |
|---|---|---|---|
| `admin.a` | ADMIN | A (Lina) | tam yetki |
| `frontdesk.a` | FRONTDESK | A (Lina) | ön büro — para görmez |
| `accounting.a` | FRONTDESK_ACCOUNTING | A (Lina) | muhasebe — para görür, maaş/hakediş görmez |
| `teacher.a` | TEACHER | A (Lina) | `sub=d1b7d93a-d8fd-44dc-b469-5ba80629c06f` → **Selin (öğretmen id=1) ile eşleşik** |
| `admin.b` | ADMIN | B (Anka) | 2. tenant admini (izolasyon testi) |
| `super.admin` | SUPER_ADMIN | **YOK** | platform sahibi; parola `Test1234!`; tenant_id YOK → iş uçlarına fail-closed (400), yalnız `/api/platform/**` |

> ✅ **İkinci tenant + SUPER_ADMIN test fixture'ları KURULU (kalıcı, dev DB'de):**
> - **Tenant A:** `11111111-1111-1111-1111-111111111111` "Lina Sanat Merkezi" (AKTIF) — ana dev tenant, tüm test verisi burada. **ASKIDA'ya ALMA** (alırsan kendi dev akışın kilitlenir; askıya alma testlerini Anka/yan tenant'larla yap).
> - **Tenant B:** `22222222-2222-2222-2222-222222222222` "Anka Akademi" (AKTIF). Kullanıcı `admin.b`. B'nin örnek veri zinciri var ("B-" önekli adlar).
> - **`super.admin`** (SUPER_ADMIN, tenant_id YOK) — platform konsolu üzerinden tenant yönetir; iş uçlarına fail-closed.
> - **İzolasyon KANITLANDI:** A↔B cross-tenant testi tüm modüllerde sıfır sızıntı (15/15 PK 404, simetri ile teyitli).
> **Yeni kullanıcılar** (admin'in `/kullanicilar`'dan açtıkları VE provisioning ile tenant açılırken yaratılan ilk admin) sabit ilk parola **`Artademi2026!`** + `must_change_password=true` ile gelir; ilk girişte kendi şifre ekranımıza kilitlenir.

> **Kullanıcı kurarken dikkat:** parola Temporary=Off olmalı ve "Required user actions" boş olmalı; yoksa token alırken `"Account is not fully set up"` hatası gelir.

### Backend Service Account (kullanıcı + provisioning için — ELLE KURULDU, yeni ortamda tekrar gerekir)
`user` modülü + platform provisioning Keycloak Admin API ile kullanıcı yaratır/günceller. Bunun için:
1. **Confidential client** `artademi-backend`: `serviceAccountsEnabled=true`, standard-flow & direct-access **OFF**. Secret → `backend/.env` `KEYCLOAK_ADMIN_CLIENT_SECRET`.
2. Service-account user'a **realm-management client rolleri:** `manage-users`, `view-users`, **`view-realm`** (⚠️ view-realm ŞART — onsuz realm rolü okunamaz/atanamaz, 403 verir).
3. Realm **User Profile**'a attribute ekle: `telefon`, `must_change_password` (`tenant_id` zaten vardı). Realm `unmanagedAttributePolicy=None` olduğu için attribute'lar declared olmak zorunda.
4. **VERIFY_PROFILE açık kalır** — backend Keycloak'a tam-temsil (merge) gönderdiği için profil bozulmaz. (Kısmi PUT profili siler → giriş bloklanır; bu bug bulundu ve `KeycloakAdminClient.updateUser` merge ile çözüldü.)

`.env` ek anahtarları: `KEYCLOAK_BASE_URL`, `KEYCLOAK_REALM=Artademi`, `KEYCLOAK_ADMIN_CLIENT_ID=artademi-backend`, `KEYCLOAK_ADMIN_CLIENT_SECRET`.

### Token alma (5 dk ömürlü)
```bash
KC=http://localhost:8080/realms/Artademi/protocol/openid-connect/token
TOKEN=$(curl -s -X POST $KC --data-urlencode client_id=artademi-app --data-urlencode grant_type=password --data-urlencode username=admin.a --data-urlencode 'password=Test1234!' | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```
> zsh'te parolayı **tek tırnak** ile yaz; tırnak takılması (`dquote>`) olursa Ctrl+C.

### Rol/Yetki Doğrulama Scripti
```bash
./scripts/verify-roles.sh   # tüm rollerin yetki matrisini test eder → PASS=20 FAIL=0
```

---

## 5. Multi-Tenant İzolasyon Mimarisi (fail-closed, kanıtlanmış)

`common/tenant/` altında:
- **TenantContext** — `ThreadLocal<UUID>`
- **TenantIdResolver** — tenant boşsa `NO_TENANT = new UUID(0,0)` (asla null → asla "tüm veriyi dök")
- **TenantAware** — `@MappedSuperclass`, `@FilterDef(autoEnabled=true)` + resolver. **Filtre HER ZAMAN aktif**; tenant boşsa boş sonuç döner.
- **TenantFilter** — JWT'den `tenant_id` claim okur, `BearerTokenAuthenticationFilter`'dan SONRA.
- **RequireTenantInterceptor** — `/api/**` için tenant boşsa 400 `TENANT_REQUIRED`. Muaf: `/api/ping`, `/actuator`, **`/api/platform/**`** (SUPER_ADMIN tenant'sız erişir).
- **TenantStatusInterceptor** — RequireTenantInterceptor'dan **SONRA** çalışır. tenant_id varsa Tenant'ı `findById` ile çeker; `status=ASKIDA` ise **403 `TENANT_SUSPENDED`**. tenant_id boşsa (SUPER_ADMIN) kontrolü ATLAR. Muaf: `/api/me(/**)`, `/api/platform/**`, `/api/ping`, `/actuator`. (Tenant kaydı bulunamazsa güvenli taraf: geçirir.)
- **SecurityConfig** — OAuth2 Resource Server; `JwtAuthenticationConverter` `realm_access.roles` → `ROLE_*`.

### ⚠️ KRİTİK GÜVENLİK KURALI — `findById` tenant sızıntısı
Hibernate `@Filter` **yalnızca JPQL/Criteria** sorgularına uygulanır, **PK find'a (`findById`) UYGULANMAZ** → başka tenant'ın kaydı id ile çekilebilir.
**ÇÖZÜM:** Tüm tenant-aware entity'lerde **`findScopedById` (JPQL)** kullan, **`findById` ASLA.** İSTİSNA: `Tenant` entity (platform) TenantAware DEĞİL → orada `findById` doğru (aşağıda 7.15). Bu kural `.claude/skills/multi-tenancy/SKILL.md`'de.

### Çapraz-tenant referans kuralı
Bir modül başka entity'ye referans verirken her id'yi `findScopedById` ile doğrular; başka tenant'a aitse 404. Çok-çoğa ilişkilerde bağlantı tablosu da `TenantAware`.

---

## 6. Ortak Altyapı (frontend'in bilmesi gerekenler)

### ApiResponse zarfı — TÜM endpoint'ler bunu döner
```json
{ "success": true, "data": {...}, "error": null, "meta": {...} }
{ "success": false, "data": null, "error": { "code": "...", "message": "...", "fields": {...} }, "meta": null }
```
- **Hata kodları:** `NOT_FOUND` (404), `CONFLICT` (409), `VALIDATION_ERROR` (400), `TENANT_REQUIRED` (400), **`TENANT_SUSPENDED` (403)**, `FORBIDDEN` (403), `INTERNAL` (500)
- **`error.fields`** — alan-bazlı validasyon hataları (form input altına basmak için).
- **`meta`** (listelerde) — `{ page, size, totalElements, totalPages }`

### Diğer
- **Para:** tüm parasal alanlar `BigDecimal`, DB `NUMERIC(12,2)`, hesapta `setScale(2, HALF_UP)`. JSON sondaki sıfırı atabilir ama değer birebir doğru.
- **Filtreler:** Spring Data Specifications.
- **Silme yok:** kayıtlar silinmez; statü/aktiflik ile yönetilir (PATCH `.../active`, `.../status`, `.../leave` vb.).

---

## 7. Backend Modülleri (HEPSİ TAMAM — commit'li, test edilmiş)

Migration sırası **V1→V16** (V13=tenant, V14=subscription, **V15=teacher_hakedis (Model C + veri göçü)**, **V16=lesson_group.hakedis_tipi + payout unique→tip**). **Toplam 205 test yeşil.** Tüm yazma uçları rol-korumalı.

### 7.1 Öğrenci — `com.artademi.student` (V3)
- Alanlar: ad/soyad/tcKimlikNo(11h)/dogumTarihi, veli bilgisi öğrenci içinde, yetiskinMi. Statü: `AKTIF/PASIF/DENEME/DONDURULMUS`.
- Kardeş eşleştirme aynı anne/baba TC üzerinden. `PATCH /{id}/status`. Validasyon: `@VeliRequired`.
- **Uçlar:** `POST/GET/PUT /api/students`, `?statu=&q=&page=&size=`, `/{id}/siblings`, `/{id}/status`
- **Yetki:** ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING; TEACHER 403. **Web ekranı VAR.**

### 7.2 Branş + Salon — `branch` + `room` (V4)
- **Uçlar:** `/api/branches`, `/api/rooms` (POST/GET/PUT, `?aktif=&q=`, `/{id}/active`). Yetki: yazma ADMIN; okuma 3 rol.

### 7.3 Öğretmen — `teacher` (V5; çoklu hakediş V15) ✅ Model C
- Alanlar: ad/soyad/telefon/email, **keycloakUserId** (Keycloak sub eşleşmesi). `TeacherBranch` açık entity.
- ⭐ **Çoklu hakediş tipi (Model C, V15):** Öğretmenin tek `hakedisTipi`+ücreti KALDIRILDI. Yerine **`TeacherHakedis`** açık entity (TenantAware): tip başına 1 satır — `SAATLIK`(saatlikUcret) / `CIRO_ORANI`(ciroOrani) / `OZEL_DERS`(dersBasiUcret). UNIQUE (teacher_id, tip). `Teacher.setHakedisler` reconcile setter (branchLinks deseni — uq insert-before-delete tuzağından kaçınır). `@HakedisTutarli` listeyi doğrular (≥1 satır, her tip ≤1, tipe göre değer zorunlu). DTO/response artık **hakediş listesi**.
- **Uçlar:** `/api/teachers` (POST/GET/PUT, `?aktif=&q=&bransId=`, `/{id}/active`). Yetki: yazma ADMIN; okuma 3 rol.

### 7.4 Grup — `group` (V6, `@Table(name="lesson_group")`; hakedis_tipi V16)
- ad, tip (`GRUP`/`OZEL`), branş+öğretmen ZORUNLU, seviye. Salon GRUP'ta zorunlu. Ücret GRUP→`aylikAidat`, OZEL→`dersBasiUcret`.
- ⭐ **`hakedisTipi` (Model C, V16):** Grup hangi hakediş tipiyle ödeneceğini taşır. Varsayılan: GRUP→`SAATLIK`, OZEL→`OZEL_DERS`; admin `CIRO_ORANI`'na çevirebilir. Payout bunu kullanır (bkz. §7.10).
- **Uçlar:** `/api/groups` (POST/GET/PUT, filtreler, `/{id}/active`). Yetki: yazma ADMIN; okuma 3 rol.

### 7.5 Kayıt/Enrollment — `enrollment` (V7) — grup transferi ✅ YENİ
- ogrenciId, grupId, kayitTarihi, durum (`AKTIF`/`AYRILDI`). Mükerrer aktif kayıt → 409. Çıkarma `PATCH /leave`.
- ⭐ **Grup transferi:** `POST /api/enrollments/{id}/transfer` body `{yeniGrupId, donem?}`. Tek transaction: eski kayıt AYRILDI + yeni gruba AKTIF + **otomatik aidat farkı** (o dönem eski grup tahakkuku ÜRETİLDİYSE: eski grup **negatif/iade** tahakkuk `−eskiAidat` + yeni grup **pozitif** `+yeniAidat`; üretilmediyse hiçbir tahakkuk açılmaz). SADECE **GRUP↔GRUP** (OZEL → 400). Cross-tenant → 404, zaten aktif → 409. ⚠️ Accrual artık **negatif tutara izin verir** (iade; DB CHECK yoktu, DTO `@Positive` yalnız create ucunda — transfer entity üzerinden negatif yazar).
- **Uçlar:** `POST/GET /api/enrollments`, filtreler, `/{id}/leave`, **`/{id}/transfer`**. Yetki: 3 rol; TEACHER 403.

### 7.6 Program — `schedule` (V8)
- grupId, gun (`HaftaGunu` enum), baslangic/bitisSaati. Çakışma (salon VEYA öğretmen) → 409.
- **Uçlar:** `/api/schedules` (POST/GET/PUT, filtreler, `/{id}/active`). Yetki: yazma ADMIN; okuma 3 rol.

### 7.7 Yoklama — `attendance` (V9)
- AttendanceSession + AttendanceEntry (`GELDI`/`GELMEDI`/`IZINLI`). Oturum açılınca AKTIF kayıtlı öğrenciler otomatik entry.
- ⭐ **AttendanceAccessGuard:** TEACHER token sub → Teacher.keycloakUserId → kendi grupları.
- **Uçlar:** `POST/GET /api/attendance-sessions`, `/{id}/entries`. Yetki: ADMIN/FRONTDESK yazma; ACCOUNTING okuma; TEACHER kendi grupları.

### 7.8 Tahsilat/Muhasebe — `finance` (V10)
- Accrual + Payment + Expense. Bakiye = SUM(tahakkuk) − SUM(ödeme). `GET /api/students/{id}/balance`, `/finance`.
- **Uçlar:** `/api/accruals`, `/api/payments`, `/api/expenses`. ⚠️ SADECE **ADMIN + FRONTDESK_ACCOUNTING**. FRONTDESK 403.

### 7.9 Otomatik Aylık Tahakkuk — `finance`
- `POST /api/accruals/uret` + `GET /api/accruals/uret-onizle`. AKTIF öğrenci + GRUP tipi grup aidatı. Idempotent. Yetki: SADECE ADMIN.

### 7.10 Hakediş — `payout` (V11; Model C V16) ✅ grup-bazında, çoklu satır
- ⭐ **Model C — hakediş tipi GRUBA bağlı, çifte sayım imkânsız.** `hesapla`/`onizle` artık **`List<PayoutResponse>`** döner. Motor öğretmenin gruplarını dolaşır; her grup KENDİ `hakedisTipi`'yle ve öğretmenin o tipe ait `TeacherHakedis` oranıyla hesaplanır, **tip başına TEK satıra** toplanır:
  - `SAATLIK` grup → grubun dönem oturum sayısı × `saatlikUcret`.
  - `OZEL_DERS` grup → grubun dönem oturum sayısı × `dersBasiUcret`.
  - `CIRO_ORANI` grup → o grubun dönem ödemeleri toplamı; net = toplam/(1+kdv/100) [varsayılan %20]; × `ciroOrani`/100.
  - Öğretmende grubun tipine ait oran satırı YOKSA → o grup ATLANIR (hata değil).
- **Karma öğretmen örnek:** SAATLIK 350 + CIRO %10, Grup-A(SAATLIK) 8 oturum=2.800 + Grup-B(CIRO) ödeme 11.800 KDV18→net 10.000×%10=1.000 → **iki satır, çakışma yok**.
- Mükerrer engeli artık **(ogretmen+donem+tip)** → 409 (V16: payout unique `(tenant,ogretmen,donem,hakedis_tipi)`). Boş sonuç (katkı sağlayan grup yok) → 400. PARA: BigDecimal scale-2 HALF_UP korunur.
- Uçlar: `/hesapla`, `/onizle`, GET, `/{id}/ode`. ⚠️ SADECE ADMIN.

### 7.11 Stok/Ürün Satışı — `inventory` (V12)
- Product + Sale (birimFiyat kopyalanır). Atomik stok düşümü; yetersiz → 409. Uçlar: `/api/products`, `/api/sales`. Yetki: ürün yazma ADMIN; satış+ürün okuma ADMIN+ACCOUNTING.

### 7.12 Raporlar — `report` (read-only)
- `/financial-summary` (ADMIN), `/student-balances` (ADMIN+ACCOUNTING), `/teacher-payouts` (ADMIN), `/group-occupancy` (3 rol). TEACHER tümüne 403.

### 7.13 Kullanıcı Yönetimi + Profil — `com.artademi.user` (Keycloak Admin API)
- **`/api/users` (SADECE ADMIN, tenant-scoped):** GET liste/`{id}`, POST, PUT, PATCH `/{id}/active`, DELETE.
- **`/api/me` (her rol):** GET (profil + `mustChangePassword` + `tenantId/tenantAdi`), PUT, POST `/change-password`.
- **KeycloakAdminClient** (RestClient, service-account token cache'li).
- ⚠️ Tenant izolasyonu: acting admin'in tenant_id'si ile hedef karşılaştırılır, uyuşmazsa 404. Yeni kullanıcı tenant_id'si admin'inkinden atanır. SUPER_ADMIN atanamaz (400). İlk parola `Artademi2026!`.

### 7.14 Tenant Entity — `com.artademi.platform` (V13)
- **Tenant** (`id UUID PK`, `ad`, `status` **AKTIF/ASKIDA/SILINDI** (SILINDI=soft-delete, §7.15), `created_at`). Seed: `1111…` Lina, `2222…` Anka (Anka yalnız DEV'de; prod'da tek tenant Lina).
- ⚠️ **Tenant entity TenantAware DEĞİL** — kendi kaydını filtrelememesi için.
- **`/api/tenant`:** GET (her rol, kendi tenant'ı), PUT (SADECE ADMIN, kendi adını). `/api/me`'ye `tenantId`+`tenantAdi` eklendi.

### 7.15 Platform / SUPER_ADMIN — `com.artademi.platform` (✅ YENİ — platform fazı)
- **`/api/platform/tenants` (SADECE SUPER_ADMIN, `@PreAuthorize hasRole('SUPER_ADMIN')`):**
  - **GET** `?status=&q=` → tenant listesi `[{id,ad,status,createdAt}]` (Specification filtreli).
  - **POST** body `{ad, adminEmail, adminAd, adminSoyad}` → tenant + **ilk ADMIN otomatik provisioning**. 201 `{tenant{...}, admin{username,email,provisioned}, warning?}`. Mükerrer ad → 409.
  - **PATCH** `/{id}/status` body `{status:"AKTIF"|"ASKIDA"}` → idempotent (aynı status → no-op 200). Bilinmeyen id → 404.
- ⚠️ Tenant TenantAware değil → düz `findById/findAll` (findScopedById kuralının TEK istisnası, kod yorumlu).
- **Provisioning sırası (a):** önce Tenant ayrı tx'te commit → sonra Keycloak admin (username email'den türer, çakışmada tenant-hex eki; parola `Artademi2026!` + must_change_password; rol ADMIN; tenant_id=yeni tenant). Keycloak patlarsa **tenant kalır + `warning`** döner (silme yok). Kısmi başarı (parola/rol adımı) → Keycloak'ta yarım user deleteUser ile temizlenir (tenant'a dokunulmaz).
- **Paket döngüsü yok:** `TenantAdminProvisioner` arayüzü platform'ta, `KeycloakTenantAdminProvisioner` impl user'da. Platform testlerinde `@MockBean` ile ağsız.
- **ASKIDA login engeli:** `status=ASKIDA` tenant'ın kullanıcısı iş uçlarına 403 `TENANT_SUSPENDED`, `/api/me` açık (bkz. §5 TenantStatusInterceptor). super.admin etkilenmez (tenant_id'siz, kontrol atlanır).
- ✅ **Tenant kullanıcı yönetimi (konsoldan):** `GET/POST /api/platform/tenants/{id}/users` + `DELETE .../users/{userId}` (SADECE SUPER_ADMIN). `TenantUserAdmin` portu (platform) + `KeycloakTenantUserAdmin` impl (user). Yeni kullanıcının tenant_id'si **PATH'ten** (body'den değil); ilk parola `Artademi2026!` + must_change_password; izolasyon fail-closed (başka tenant kullanıcısı listede/silmede 404).
- ✅ **Tenant soft-delete:** `DELETE /api/platform/tenants/{id}` → `status=SILINDI` (yeni TenantStatus değeri). Listeden gizlenir (varsayılan liste SILINDI'yi atar; `?status=SILINDI` ile görünür), kullanıcıları iş uçlarından kilitlenir (TenantStatusInterceptor ASKIDA+SILINDI keser). **Veri silinmez**, status'u AKTIF'e çevirerek geri alınabilir. (Junk test tenant'ların kalıcı silinmesi prod'da elle psql+kcadm ile yapılır.)
- ✅ **CORS (prod) — ÇÖZÜLDÜ (commit 61e4a1a):** SecurityConfig CORS allowed-origins **env-driven** (`APP_CORS_ALLOWED_ORIGINS`, virgülle; varsayılan localhost). Prod: `https://app.artademi.com,https://artademi.com`. Tek-domain'de tarayıcı same-origin POST'ta bile `Origin` header gönderir → eski localhost-only liste 403 "Invalid CORS request" veriyordu (GET/curl Origin göndermediği için geçiyordu). `setAllowCredentials(true)` → wildcard yasak, origin'ler açık listelenir.

### 7.16 Subscription + grace/ASKIDA otomasyonu — `com.artademi.platform` (✅ YENİ — V14)
- **Subscription** entity (Tenant'a 1-1, `tenant_id` UNIQUE FK). ⚠️ TenantAware DEĞİL (Tenant gibi, platform-düzeyi; `findByTenantId` doğru — findScopedById istisnası).
- Alanlar: `plan` (DENEME/AYLIK), `status` (DENEME/AKTIF/ODEME_BEKLIYOR/ASKIDA/IPTAL), `currentPeriodStart/End`, `graceEndsAt` (nullable), `paymentStatus` (BEKLIYOR/ODENDI/BASARISIZ).
- **Status akışı:** `DENEME → AKTIF → ODEME_BEKLIYOR (grace, TAM ERİŞİM + uyarı) → ASKIDA (kesinti)`. Ayrıca `IPTAL` (manuel).
- **Grace = 14 gün.** Net ayrım: **grace = uyarı (tenant.status AKTIF kalır), ASKIDA = kesinti (tenant.status ASKIDA).** Sadece ASKIDA geçişi tenant.status'a dokunur → mevcut TenantStatusInterceptor DEĞİŞMEDİ.
- **`SubscriptionService.evaluate(now)`** (deterministik, scheduler'dan bağımsız test edilir): dönem bitti+ödeme yok → ODEME_BEKLIYOR+grace; grace bitti+ödeme yok → tenant ASKIDA; ödeme ODENDI → AKTIF (telafi).
- **`@Scheduled(cron="0 0 3 * * *")`** + `@EnableScheduling` günlük `evaluate` çağırır. Test evaluate'i DOĞRUDAN çağırır.
- **Provisioning entegrasyonu:** yeni tenant otomatik **DENEME trial** (14g) ile açılır (tenant commit sonrası `createTrial`, try/catch).
- **Uçlar:** GET `/api/platform/tenants` listesine subscription özeti eklendi; `PATCH /api/platform/tenants/{id}/subscription` (SUPER_ADMIN, manuel ödeme işaretle/dönem ilerlet — `markPaid`).
- **Uyarı bayrağı:** `/api/me` cevabına `subscriptionWarning {inGrace, graceEndsAt, message}` (grace'teyse dolu, değilse null). Frontend banner için (banner henüz YOK).
- ⚠️ **Dev seed:** V14 `INSERT...SELECT FROM tenant` → mevcut tüm tenant'lara AKTIF/ODENDI/uzak-dönem(2030) subscription (dev'de otomatik ASKIDA tetiklenmesin). **Lina/Anka'yı evaluate testlerinde kullanma.**


### 7.17 Dashboard — `com.artademi.dashboard` (✅ YENİ, read-only, migration YOK)
- **`GET /api/dashboard`** (her iş rolü; içerik role göre değişir). Tek uç, token rolüne göre farklı `data`.
- ⚠️ **Güvenlik tip-düzeyinde:** 4 ayrı sealed DTO (Admin/Accounting/Frontdesk/Teacher). İzinsiz alan HİÇ serialize edilmez (FRONTDESK cevabında parasal anahtar literal olarak YOK — null değil). "Frontend gizleme" değil, backend filtreler.
- **ADMIN:** sayılar (aktifÖğrenci/grup, buAyTahsilat/gider/net, bekleyenBorç) + trend6Ay (tahsilat/gider/net) + sonÖdemeler + sonÖğrenciler + bugünDersler + subscriptionWarning.
- **FRONTDESK_ACCOUNTING:** tahsilat+borç+trend(tahsilat); gider/net YOK, hakediş YOK.
- **FRONTDESK:** yalnız öğrenci/grup sayısı + dersler + öğrenciler; **PARA YOK**.
- **TEACHER:** kendiGruplar (öğrenci sayısı) + bugünDersler + sonYoklamalar; sadece kendi (CurrentTeacherResolver); para YOK.
- Rol önceliği: ADMIN > ACCOUNTING > FRONTDESK > TEACHER. super.admin → 400 (iş ucu). Mevcut report/finance/student/group servisleri yeniden kullanıldı (kopya yok).
### 7.18 Şube (fiziksel lokasyon) — `com.artademi.sube` (✅ YENİ, V23)
- ⚠️ **İSİMLENDİRME TUZAĞI — en kritik nokta:** `com.artademi.branch.Branch` = **BRANŞ** (Bale, Piyano), tablo `branches`. `com.artademi.sube.Sube` = **ŞUBE** (Kadıköy Şubesi), tablo `sube`. İkisi FARKLI kavram; `branch` adı branşta kullanıldığı için şube Türkçe adlandırıldı.
- **`/api/subeler`** CRUD + `PATCH /{id}/active` (yazma ADMIN, okuma ADMIN/FRONTDESK/FRONTDESK_ACCOUNTING, TEACHER 403). DELETE YOK.
- Alanlar: `ad` (zorunlu), `adres`, `telefon`, `aktif`. Silme yerine pasifleştirme.
- **Şube OPSİYONELDİR:** `rooms.sube_id` ve `lesson_group.sube_id` NULLABLE. Tek lokasyonlu kurum hiç şube tanımlamadan çalışır; NOT NULL yapmak mevcut kurumları bozardı. Web'de de şube tanımlı değilse form alanı **hiç gösterilmez**.
- ⚠️ **Çapraz-tenant koruması:** FK tek başına başka tenant'ın şubesine referansı ENGELLEMEZ. `RoomService.resolveSube` / `GroupService.resolveSube` `findScopedById` ile çözer → yabancı id 404. `SubeControllerTest.salonaBaskaTenantinSubesiAtanamaz_404` bunu açıkça doğrular.
- Grubun şubesi salondan TÜRETİLMEZ, ayrı tutulur: salonu olmayan (ÖZEL) grubun da şubesi olabilir.
- Web: `/subeler` liste + form (menü "Tanımlar → Şubeler", branşın üstünde). Salon ve grup formlarında şube seçici; düzenlemede pasif şube seçiliyse listeye sentezlenip görünür kalır.

### 7.19 Belgeler (PDF) — `com.artademi.belge` (✅ YENİ, migration YOK)
- **`GET /api/payments/{id}/makbuz.pdf`** — tahsilat makbuzu. Yetki: **ADMIN + FRONTDESK_ACCOUNTING** (parasal belge; ön büro erişemez).
- **`GET /api/students/{id}/kayit-formu.pdf`** — öğrenci kayıt formu. Yetki: ADMIN + FRONTDESK + FRONTDESK_ACCOUNTING (formda para YOK, ön büro basabilir).
- İkisi de `findScopedById` ile yüklenir → başka kurumun kaydı 404.
- ⚠️ **TÜRKÇE PDF TUZAĞI (en kritik nokta):** PDF'in yerleşik (base-14) fontları WinAnsi/Latin-1 kodlar, bu kümede **ş, ğ, İ, ı YOKTUR**. Gömülü font kullanılmazsa bu harfler bozuk basılır ve **hata SESSİZDİR** — PDF yine üretilir, sadece yanlış görünür. Bu yüzden `PdfFontlari` DejaVu Sans'ı `IDENTITY_H` ile **gömülü** yükler. `src/main/resources/fonts/` (lisans: DEJAVU-LICENSE.txt, gömmeye izinli). Testler fontun gömüldüğünü bayt düzeyinde doğrular.
- ⚠️ `PageSize.A5.rotate()` KULLANMAYIN: sayfaya 90° rotasyon bayrağı koyar, okuyucular içeriği yan çevirir (ilk denemede böyle oldu, görsel kontrolde yakalandı).
- ⚠️ `TenantService.currentName()` sözleşmesi gereği **null dönebilir**; belgeye "null" basmamak için kurum adı yoksa başlık/dipnot tamamen atlanır.
- Makbuz numarası ayrı sayaç DEĞİL, tahsilat id'sidir (ayrı sayaç boşluk/mükerrer numara riski doğurur).
- `TutarYaziya` — makbuzun "YALNIZ …" satırı. Türkçe'ye özgü iki kural test altında: **"bir bin" DENMEZ** (1.000 = "bin") ve **"bir yüz" DENMEZ** (100 = "yüz"); ama 1.000.000 = "bir milyon" (orada "bir" korunur). Kuruş HALF_UP yuvarlanır ki rakam ile yazı birbirini tutsun.
- Kütüphane: **OpenPDF 3.0.5** (LGPL — Maven bağımlılığı olarak SaaS'ta uygun; uygulama son kullanıcıya dağıtılmıyor). ⚠️ 3.x'te paket adı `com.lowagie.text` DEĞİL, **`org.openpdf.text`**.
- Web: tahsilat listesinde satır başına "Makbuz (PDF)", öğrenci detayında "Kayıt Formu (PDF)".

### 7.20 Online Ön Kayıt (Başvuru) — `com.artademi.basvuru` (✅ YENİ, V24)

Kurum kendi public başvuru bağlantısını paylaşır; veli JWT olmadan form doldurur, talep kurumun listesine düşer ve oradan öğrenciye dönüştürülür.

**Uçlar**
- `GET /api/public/basvuru/{slug}` — form bilgisi (kurum adı + aktif branşlar). **Kimlik YOK.**
- `POST /api/public/basvuru/{slug}` — başvuru gönder. **Kimlik YOK.**
- `GET /api/basvurular` (+ `/{id}`, `/yeni-sayisi`) — ADMIN + ön büro
- `PATCH /api/basvurular/{id}/durum`
- `POST /api/basvurular/{id}/ogrenciye-donustur`
- `PUT /api/tenant/basvuru-slug` — bağlantı adını belirle/kaldır (**yalnız ADMIN**)

**⚠️ EN ÖNEMLİ NOKTA — tenant kuralının tek istisnası.**
Projenin demir kuralı "tenant YALNIZCA JWT'den okunur"dur. Burada JWT yoktur: tenant **URL'deki slug**'dan çözülür. İstisnayı güvenli kılan sınırlar (hepsi test altında):
1. **Slug yetki taşımaz** — yalnızca "hangi kurumun formu" sorusunu yanıtlar; kurum bu bağlantıyı zaten kamuya duyurur.
2. **Yüzey iki uçtan ibarettir** — form bilgisi okuma + başvuru yazma. Başka hiçbir iş verisi bu yoldan okunamaz. Form yanıtı yalnızca kurum adı ve branş adları taşır.
3. **Yalnızca AKTIF kurum** — ASKIDA/SILINDI kurumda form 404. Ödemesi duran kurum altyapımız üzerinden talep toplayamaz. (`/api/public/**` TenantStatusInterceptor'dan muaf olduğu için bu kontrol serviste ELLE yapılır.)
4. **Bağlam dar kapsamlı** — `TenantContext` yalnızca işlem süresince set edilir ve `finally`'de ÖNCEKİ değerine geri alınır (blanket `clear()` değil: istek kimlikli de gelmiş olabilir). `PublicBasvuruTest.kimliksizIstek_TenantContextSizdirmaz` bunu kilitler; bağlam thread'de kalsaydı havuzdaki thread bir sonraki isteğe tenant taşırdı.

**Kötüye kullanım koruması**
- Honeypot (`website` alanı) — dolu ise kayıt açılmaz ama **200 döner** (bota engellendiğini sezdirmeyiz).
- Soğuma: **(slug + IP)** başına 60 sn. ⚠️ Anahtar yalnızca IP DEĞİLDİR — ortak IP arkasındaki (NAT/ofis/site) iki farklı veli birbirini engellememeli; sadece IP ile anahtarlamak gerçek bir talebi kaybettirirdi. Test: `soguma_ayniIpFarkliForm_ENGELLENMEZ`.
- Mükerrer telefon: aynı telefon 24 saat içinde tekrar gönderirse ikinci kayıt açılmaz, ama kullanıcıya **başarı** gösterilir (yoksa "gitmedi mi?" diye tekrar tekrar dener).
- Soğuma haritası 10.000 girdiyi aşınca budanır (public uçta sınırsız büyüme olmasın).

**Öğrenciye dönüştürme**
- TC + doğum tarihi **formda sorulmaz**, dönüştürmede istenir: form sürtünmesi azalsın ve ilgilenilmemiş bir talepten gereksiz kişisel veri toplanmasın.
- Öğrenci yetişkin değilse anne VEYA baba ad+TC zorunlu — `@VeliRequired` ile öğrenci formundaki **aynı** validator kullanılır (iki yer ayrışıp tutarsızlaşmasın).
- Öğrenci **elle kurulmaz**, `StudentMapper.toNewEntity` kullanılır ki oluşturma değişmezleri (başlangıç statüsü DENEME) tek yerde kalsın. Elle kurulduğunda `status` boş kalıp NOT NULL kısıtına takılmıştı.
- Mükerrer dönüşüm 409 — aynı kişinin iki öğrenci kaydı tahakkuk/yoklamayı böler.
- `OGRENCIYE_DONUSTU` durumu **elle atanamaz** (409); yoksa öğrencisi olmayan "dönüştürüldü" kayıtları oluşur ve liste yalan söyler.

**Bildirim**
- Yeni başvuruda kurumun ADMIN kullanıcılarına mail (Keycloak'tan adresler). Gönderim **tamamen try/catch içinde**: SMTP ya da Keycloak erişilemezken veli formu dolduramaz ve talep kaybolurdu — kaydın durması bildirimden önemlidir.

**Web**
- ⚠️ `main.tsx` bootstrap'ta dallanır: yol `/basvuru/` ile başlıyorsa **Keycloak HİÇ başlatılmaz**. `initKeycloak()` `login-required` ile çalışır, yani çağrıldığı anda veliyi giriş ekranına atardı.
- Public sayfa `api/publicClient.ts` kullanır — paylaşılan `api` istemcisinin istek interceptor'ı her çağrıda `keycloak.updateToken()` çağırıp başarısızlıkta `login()`'e yönlendirdiği için o istemci public sayfada KULLANILAMAZ.
- SPA fallback (`nginx-spa.conf` `try_files`) ve Caddy yönlendirmesi zaten uygun; ek yapılandırma gerekmedi.
- Ekranlar: `/basvurular` (liste + dönüştürme modalı), bağlantı kartı (yalnız ADMIN), menüde "Ön Kayıt".

### 7.21 Otomatik Bildirim Servisi — `com.artademi.bildirim` (✅ YENİ, V25)

Üç otomatik gönderim, **hepsi kurum başına opt-in ve varsayılan KAPALI**:
1. **Otomatik borç hatırlatma** — her sabah 04:30; `BorcHatirlatmaService.otomatikGonder()`
2. **Devamsızlık bildirimi** — her akşam 20:00, o günün `GELMEDI` kayıtları
3. **Haftalık finansal özet** — seçilen ISO günde (1=Pzt … 7=Paz), yöneticilere

**Uçlar:** `GET|PUT /api/bildirim-ayarlari` (**yalnız ADMIN** — bu ayarlar kurumun VELİLERİNE otomatik mail göndermeyi başlatır, ön büro tek başına açamaz). Web: `/bildirim-ayarlari` (Sistem bölümü).

**⚠️ Neden varsayılan KAPALI.** `BorcHatirlatmaService` javadoc'unda bilinçli bir karar yazılıydı: *"otomatik borç takibi, okulun velisiyle ilişkisini yönetmesini elinden alır"*. Otomatikleştirme bu kararı **iptal etmez**, kurumun tercihine bırakır — açmayan kurum bugünkü elle akışta kalır. Javadoc da buna göre güncellendi. Test: `varsayilan_HEPSI_KAPALI`.

**⚠️ EN KRİTİK NOKTA — zamanlanmış iş + multi-tenant.**
Zamanlanmış iş bir İSTEKTEN doğmaz; `TenantContext` **boştur** ve fail-closed filtre yüzünden tüm TenantAware sorgular BOŞ döner. `BildirimScheduler` bu yüzden kurumları tek tek dolaşıp bağlamı **kendisi kurar**. Bu, tenant kuralının **ikinci** bilinçli istisnasıdır (birincisi §7.20 public başvuru). Güvenli kılan sınırlar — hepsi `BildirimSchedulerTest`'te kilitli:
- Tenant listesi **platform tablosundan** gelir (istemci girdisi yok, sahtelenemez)
- **Yalnızca AKTIF** kurumlar işlenir — askıdaki kurumun velisine mail gitmez
- Bağlam her kurumdan sonra `finally` ile temizlenir; sızarsa **bir sonraki kurumun işi yanlış tenant'ta çalışır**
- Bir kurumun hatası diğerlerini **durdurmaz** (tek tek try/catch) — aksi halde tek bozuk kurum tüm platformun bildirimlerini susturur

**Mükerrer kalkanları (itibar koruması).** Mailler bizim alan adımızdan gidiyor; veliler spam işaretlerse KENDİ ödeme uyarılarımız da spam'e düşer.
- Borç: mevcut **7 gün soğuma** + **günlük 50 tavan** otomatik yolda da aynen geçerli; tavan aşılırsa kalanlar ertesi gün
- Devamsızlık: `devamsizlik_bildirimi` tablosu (ogrenci_id, oturum_id) — **DB'de unique index**, uygulama kontrolü kaçsa bile mükerrer kayıt olmaz. Job tekrar çalışsa da veli iki kez uyarılmaz. Günlük tavan 200.
- Veli e-postası yoksa **sessizce atlanır**, iş patlamaz

**Zamanlama gerekçeleri:** devamsızlık **akşam** çalışır çünkü veli "bugün gelmedi" bilgisini aynı gün ister; sabah işi **04:30**'dur çünkü abonelik işi (03:00) askıya alma/ödeme durumlarını günceller, bildirimler güncel durum üzerinden gitmelidir.

**Haftalık özet** ayrı bir hafta raporu uydurmak yerine mevcut, test edilmiş `financialSummary` (içinde bulunulan ay) kullanır — yönetici zaten aylık rakamla çalışıyor.

### 7.22 Şifreli Kurum Ayarı — `com.artademi.gizli` (✅ YENİ, V26)

Kurum bazlı gizli değerleri (SMS/WhatsApp kimlik bilgileri) **AES-256-GCM** ile şifreli saklar.

**Neden gerekli:** bugüne kadar tek dış entegrasyon iyzico ve anahtarı TEK + platform düzeyinde (`.env`). SMS/WhatsApp'ta ise **her kurumun kendi kimlik bilgisi** olur ve veritabanında durmak zorundadır. Düz metin kabul edilemez: yedek sızarsa tüm müşterilerimizin sağlayıcı hesapları ele geçer.

**Yapılandırma:** `ARTADEMI_SIFRELEME_ANAHTARI` — base64, **32 bayt**. Üretmek için `openssl rand -base64 32`. Boş bırakılabilir (uygulama açılır) ama o zaman gizli ayar yazılamaz/okunamaz.

**Dört tasarım kararı (hepsi test altında):**
1. **GCM, CBC değil.** Kimlik doğrulamalı: DB'deki değer kurcalanırsa çözme **patlar**. CBC olsaydı çöplükle çözülür, biz de onu sağlayıcıya "kullanıcı adı" diye gönderirdik. Test: `kurcalananDeger_COZULMEZ_sessizceGecmez`.
2. **Her şifrelemede yeni IV.** Aynı düz metin her seferinde farklı çıktı. Sabit IV olsaydı iki kurumun aynı parolayı kullandığı DB'ye bakmakla anlaşılırdı. Test: `ayniDuzMetin_FARKLI_sifreliMetinUretir`.
3. **`v1:` sürüm öneki.** Saklanan biçim `v1:<base64 IV>:<base64 şifreli>`. Anahtar rotasyonunda eski kayıtlar v1 ile okunmaya devam eder, yeniler v2 yazılır. Öneksiz rotasyon tüm tabloyu tek seferde dönüştürmeyi gerektirirdi.
4. **Fail-closed.** Anahtar yoksa şifreleme de çözme de hata verir; yanlış uzunlukta anahtar **açılışta** patlar. "Anahtar yoksa düz metin yaz" kolaylığı korumayı sessizce yok ederdi.

**Sızıntı önlemleri:**
- Arayüze yalnızca **maske** gider (`••••4821`); 4 karakterden kısa değerler tamamen maskelenir (kısa bir değerin "son 4 hanesi" değerin kendisidir).
- `GizliAyarService.oku()` düz değer döner ve **yalnızca sunucu içi entegrasyon kodu içindir** — HTTP yanıtına, loga veya hata mesajına ASLA konmaz.
- Şifreleme hatalarında istisna **sebep zinciri taşımaz**; bazı kütüphaneler istisna mesajında girdi parçası taşıyabiliyor.
- `GizliAyar.getDegerSifreli()` package-private.

**Kurcalanmış kayıtta sessizce "yok" DENMEZ:** entegrasyonun "hiç yapılandırılmamış" sanması gerçek sorunu gizlerdi.

**⚠️ Henüz controller/ekran YOK — bilinçli.** Hangi alanların saklanacağı SMS sağlayıcısı seçilmeden belli değil (Netgsm'in istediği alanlar İletimerkezi'ninkinden farklı). Genel "anahtar/değer gir" ekranı kullanıcıya ham anahtar yazdırmak olurdu. Sağlayıcı belli olunca ekran kısa iş.

### ⚠️ Saat dilimi tuzağı (V25 düzeltmesi)

Konteynerde `TZ` **ayarlı değil**, JVM UTC çalışıyor. `@Scheduled(cron = ...)` `zone` belirtilmezse "akşam 20:00" aslında **TR 23:00**'te tetiklenir — veliye gece yarısı mail. `BildirimScheduler` artık `zone = "Europe/Istanbul"` kullanıyor ve tarihi `LocalDate.now(TURKIYE)` ile alıyor (UTC günü, gün dönümüne yakın saatlerde yanlış güne bakar).

NOT: `SubscriptionScheduler` zone belirtmez → UTC 03:00 = TR 06:00. Sıralama korunduğu (bildirimlerden önce) ve "düşük trafik" amacı bozulmadığı için değiştirilmedi.

### 7.23 Kasa + Tedarikçi — `com.artademi.kasa` / `com.artademi.tedarikci` (✅ YENİ, V27)

Dalga 2'nin ilk maddesi: rakipteki finans derinliği farkı (kasa tanımı, tedarikçi, kâr-zarar) kapatılmaya başlandı.

**Uçlar** — hepsi **ADMIN + FRONTDESK_ACCOUNTING** (kasa bakiyesi ve "toplam ödenen" PARASAL bilgidir, ön büro görmez):
- `GET|POST|PUT /api/kasalar`, `PATCH /api/kasalar/{id}/durum`
- `GET /api/kasalar/{id}/hareketler`, `POST /api/kasalar/{id}/duzeltme`
- `POST /api/kasalar/transfer`, `DELETE /api/kasalar/hareketler/{id}`
- `GET|POST|PUT /api/tedarikciler`, `PATCH /api/tedarikciler/{id}/durum`

**⚠️ BAKİYE SAKLANMAZ, HESAPLANIR.**
`açılış + tahsilatlar − giderler + hareket girişleri − hareket çıkışları`. Saklanan bakiye zamanla gerçekten sapar: bir tahsilat elle düzeltilir, bir gider silinir, bir güncelleme kaçar ve kimse fark etmez. Aynı şey `tedarikci.toplamOdenen` için de geçerli — giderlerden hesaplanır.

**⚠️ TRANSFER İKİ SATIRDIR.** Kaynakta `CIKIS`, hedefte `GIRIS`, ortak `transfer_grubu` (UUID) ile bağlı. Tek satır olsaydı her bakiye sorgusu "bu satır bana giriş mi çıkış mı" diye iki yöne bakmak zorunda kalırdı. Silme **grup üzerinden** yapılır — tek bacağı silmek kasalar arasında kaybolmuş para bırakırdı. Test: `transferSilme_IKI_bacagiBirdenSiler`.

**⚠️ Tahsilat/gider `kasa_hareketi` tablosunda DEĞİLDİR.** Kendi tablolarında durur, kasaya `kasa_id` ile bağlanır. İkisine birden yazılsaydı aynı para iki kez sayılırdı. `kasa_hareketi` yalnızca transfer + elle düzeltme taşır.

**Tutar her zaman pozitif; yön `yon` alanındadır.** Negatif tutara izin verilseydi "eksi giriş" ile "artı çıkış" aynı şeyi iki biçimde ifade eder, raporlar çaprazlanırdı. DB'de `CHECK (tutar > 0)`.

**Açılış bakiyesi** bilinçli: sisteme geçmeden önceki tutarı girmek, geçmiş hareketleri tek tek girmek zorunda kalmadan doğru bakiye göstermenin tek yolu.

**`kasa_id` / `tedarikci_id` NULLABLE** — (a) mevcut kayıtların kasası yok, NOT NULL migration'ı çalışan kurumları bozar; (b) kasa kullanmak zorunlu değil. Arayüzde de hiç kasa tanımlı değilse seçici **hiç görünmez**.

**Çapraz-tenant koruması:** FK aynı tenant'ı garanti etmez; `PaymentService.resolveKasa` ve `ExpenseService.resolveKasa/resolveTedarikci` `findScopedById` kullanır. Testler yabancı kasa/tedarikçi id'siyle 404 döndüğünü doğrular.

**⚠️ Tedarikçi CARİ HESAP DEĞİLDİR.** Fatura/borç-alacak takibi yok; yalnızca giderlerin kime yapıldığı ve toplam. Gerçek cari, fatura ve ödeme kalemlerini ayrı modellemeyi gerektirir — kapsam dışı bırakıldı, kodda ve DTO'da açıkça yazılı.

**Silme YOK:** kasa ve tedarikçi `aktif` ile pasifleştirilir; geçmiş tahsilat/giderler bağlı kalır.

### 7.24 Telafi Dersi Hakkı — `com.artademi.telafi` (✅ YENİ, V28)

Öğrenci derse gelmediğinde kurum telafi hakkı tanıyabilir; hak sonradan bir derste kullanılır ve iz bırakır.

**Uçlar** — **ofis rolleri** (ADMIN + FRONTDESK + FRONTDESK_ACCOUNTING; telafi takibi ön büro işidir ve parasal bilgi taşımaz; TEACHER erişemez):
- `GET /api/telafi` (durum/öğrenci filtresi), `GET /api/telafi/{id}`, `GET /api/telafi/bekleyen-sayisi`
- `GET /api/telafi/adaylar` — hak verilebilecek devamsızlıklar (son 60 gün)
- `POST /api/telafi` — hak ver
- `POST /api/telafi/{id}/kullan`, `POST /api/telafi/{id}/iptal`

Web: `/telafi` (Eğitim bölümü).

**⚠️ HAK OTOMATİK DOĞMAZ.** Her `GELMEDI` kaydından otomatik hak üretilseydi liste kullanılamaz hale gelirdi (bir dönemde yüzlerce devamsızlık olur) ve kurumun kendi kuralı ("haber verdiyse telafi veririm") ezilirdi. `/adaylar` yalnızca **öneri** listesidir; hakkı kurum tanır. Hak verilen devamsızlık listeden çıkar.

**⚠️ SÜRE DOLMASI SAKLANMAZ, HESAPLANIR.** `SURESI_DOLDU` diye bir durum **yoktur** — olsaydı onu her gece güncelleyen ayrı bir job gerekirdi ve job kaçarsa durum yalan söylerdi. `son_kullanma_tarihi` tutulur, "doldu mu" sorusu okuma anında `TelafiHakki.suresiDolduMu(bugün)` ile cevaplanır. `son_kullanma_tarihi` NULL = süresiz.

**Çakışma kuralları (hepsi 409, hepsi testli):**
- Aynı devamsızlıktan **ikinci hak verilemez** — bir devamsızlık iki telafi dersi doğurmamalı. DB'de kısmi unique indeks (`kaynak_oturum_id IS NOT NULL` iken); kaynaksız (elle tanımlanan) haklar bu kısıttan muaf, yoksa kurum ikinci bir elle hak tanımlayamazdı.
- Kullanılmış hak tekrar kullanılamaz (aynı telafiyi iki kez saymak olurdu)
- İptal edilmiş hak kullanılamaz (geri alınmış hakkı diriltmek olurdu)
- Süresi dolmuş hak kullanılamaz (süre koymanın anlamı budur)
- Kullanılmış hak iptal edilemez

**Kullanım kanıt ister:** `kullanilanOturumId` zorunlu — hangi derste telafi edildiği kayda geçmeden "kullanıldı" demek izsiz kalırdı.

**Silme YOK:** hak `IPTAL` ile geri alınır; kimin ne zaman hak kazandığı ve kullandığı izi korunur.

**Çapraz-tenant:** öğrenci ve oturum `findScopedById` ile çözülür; yabancı id 404 (testli).

### 7.25 Ders Paketi (Kontör) — `com.artademi.paket` (✅ YENİ, V29)

Dalga 2'nin son maddesi. **Üçüncü fiyatlandırma modeli**: mevcut ikisi grup üzerindeydi (`aylik_aidat`, `ders_basi_ucret`); paket **öğrenci** üzerindedir — "10 derslik bale paketi, 4.000 TL".

**Uçlar** — **ADMIN + FRONTDESK_ACCOUNTING** (satış tahakkuk üretir, PARASAL işlemdir):
`GET /api/paketler` (ogrenciId filtresi), `GET /{id}`, `POST /api/paketler` (sat), `POST /{id}/iptal`. Web: Finans → **Ders Paketleri** sekmesi.

**⚠️ KONTÖR DÜŞÜMÜ YOKLAMA DURUMUNA BAĞLI:**

| Durum | Kontör |
|---|---|
| `GELDI` | düşer |
| `GELMEDI` (habersiz) | **düşer** — okul dersi tahsis etti |
| `IZINLI` (haber vermiş) | düşmez; daha önce düşülmüşse **geri alınır** |

Bu ayrım mevcut `YoklamaDurumu` ile birebir örtüşüyor — "haber verdi mi" diye ayrı bir alan eklemeye gerek kalmadı. Türkiye'deki yaygın uygulama da bu.

**⚠️ KALAN DERS SAKLANMAZ, HESAPLANIR.** Tüketilen her ders bir `paket_kullanim` **satırıdır**; kalan = `toplam_ders` − satır sayısı. Sayaç tutulsaydı yoklama düzeltmesinde (GELDI → IZINLI) geri alma adımı kaçabilir ve sapma sessiz kalırdı. Satır silinince kalan kendiliğinden geri gelir. Test: `IZINLI_kontorDUSMEZ_onceDusulduyseGERI_ALINIR`.

**⚠️ Benzersizlik anahtarı (öğrenci, oturum) — paket DEĞİL.** Öğrencinin iki paketi varsa aynı dersten iki kontör düşmemeli. DB'de unique index; ayrıca idempotans sağlar (yoklama iki kez kaydedilirse kontör bir kez düşer).

**⚠️ KONTÖR BİTİNCE YOKLAMA ENGELLENMEZ.** Kontörü biten öğrenci derse yazılmaya devam eder; sadece düşüm yapılmaz. Yoklama alınamaması öğretmeni sistem dışına iter — paket takibi bunu hak etmez. Liste "kontör bitti" gösterir. Kalan negatife düşmez.

**FIFO + grup önceliği:** düşüm sırası (1) oturumun grubuna bağlı paketler, (2) grubu olmayan genel paketler; her ikisinde de en eski satış önce. Aksi halde süresi yaklaşan paket boşta kalırken yeni paket harcanır ve öğrenci hak kaybeder.

**Satış PEŞİN tek tahakkuk üretir** (`accrual_id` ile bağlı, açıklaması "Ders paketi: …"). Taksit isteyen kurum tahakkuku elle böler; otomatik taksit "kaç taksit, hangi tarihlerde" gibi kurumdan kuruma değişen kurallar gerektirir — kapsam dışı.

**⚠️ Paket iptalinde tahakkuk OTOMATİK SİLİNMEZ.** Tahsilat yapılmış olabilir; iade/mahsup kurumun kararı olan bir finans işlemidir. Silmek yapılmış tahsilatı sahipsiz bırakırdı. Arayüzde bu uyarı gösterilir.

**`BITTI` diye bir durum YOK** — kalan ders hesaplanan bir değer; "bitti" durumu tutmak, yoklama düzeltmesiyle kontör geri geldiğinde durumu da geri almayı gerektirirdi ve o adım kaçarsa durum yalan söylerdi.

**Entegrasyon noktası:** `AttendanceService.updateEntries` → `PaketService.yoklamaDegisti(...)`. Bu çağrı yoklamayı asla engellemez; paketi olmayan öğrencide hiçbir şey yapmaz.

---

### 7.26 Öğrenci statüsü: DENEME→AKTİF geçişi ELLE (ürün kararı 2026-09-09)

Test ekibi: "Öğrenciyi gruba atıyorum, yoklamasını alıyorum; aktif listesinde çıkmıyor." Doğrulandı
(kod + kırmızı test + tarayıcı/DB): yeni öğrenci `DENEME` doğar (`StudentMapper.toNewEntity`),
gruba kayıt ve yoklama statüyü değiştirmez, "Aktif" sekmesi `status=AKTIF` filtresidir ve
`findAktifAidatliKayitlar` yalnız AKTİF öğrenciye aidat üretir (mali etki: Deneme'de unutulan
öğrenciye fatura kesilmez).

**Karar: otomatik geçiş YOK; kurum elle Aktif yapar, sistem iki yerde uyarır.**
- Grup ekranı: `EnrollmentResponse.OgrenciRef.status` eklendi. `GroupDetailPage` — seçicide
  Aktif olmayanlara rozet; Deneme öğrenci eklenince amber uyarı + "öğrenciyi Aktif yapın" bağlantısı
  (`/ogrenciler/:id/duzenle`); listede Deneme rozeti (bağlantılı) ve "Bu grupta N deneme öğrencisi var" satırı.
- Otomatik tahakkuk: `AccrualGenerationResult.atlananDenemeOgrenciler` (id, ad, soyad, grupId, grupAd) —
  `EnrollmentRepository.findDenemeAidatliKayitlar()` ile; **sayaçlara dahil değil**. Önizleme ve üretimde
  döner; `OtomatikTahakkukTab` "N deneme öğrencisi aidat almayacak" bloğu + "Aktif yap" bağlantıları.
  Aktif yapıp aynı dönemi tekrar üretmek yeterli (idempotent, yalnız eksikler eklenir).
- Regresyon testi: `student/OgrenciAktiflesmeTest` — DENEME'de kalma, kayıt yanıtında statü, önizleme/üretim
  uyarı listesi, `PATCH /api/students/{id}/status` AKTİF sonrası üretim.
- Reddedilen seçenekler (tekrar gündeme gelirse): (A) kayıt=AKTİF — deneme dersine gelen de faturalanır;
  (B) kayıt formunda "deneme dersi" kutusu — en dengeli ama kurum akışına ek alan.

## 8. Yetki Matrisi Özeti (frontend'de menü/buton gizleme için kritik)

| Alan | ADMIN | FRONTDESK | FRONTDESK_ACCOUNTING | TEACHER | SUPER_ADMIN |
|---|:--:|:--:|:--:|:--:|:--:|
| Öğrenci/Grup/Kayıt (operasyon) | ✅ | ✅ | ✅ | ❌ | ❌ (400) |
| Branş/Salon/Öğretmen/Grup/Program **yazma** | ✅ | ❌ | ❌ | ❌ | ❌ |
| Branş/Salon/Öğretmen/Grup/Program **okuma** | ✅ | ✅ | ✅ | ❌ | ❌ |
| Finans (tahakkuk/ödeme/gider/bakiye) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Hakediş (maaş) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Stok ürün yazma | ✅ | ❌ | ❌ | ❌ | ❌ |
| Stok satış + ürün okuma | ✅ | ❌ | ✅ | ❌ | ❌ |
| Rapor: finansal özet / hakediş özeti | ✅ | ❌ | ❌ | ❌ | ❌ |
| Rapor: öğrenci borç listesi | ✅ | ❌ | ✅ | ❌ | ❌ |
| Rapor: grup doluluk | ✅ | ✅ | ✅ | ❌ | ❌ |
| Yoklama | ✅ | ✅ | (okuma) | **kendi grupları** | ❌ |
| Kullanıcı yönetimi (`/api/users`) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Profil (`/api/me`) | ✅ | ✅ | ✅ | ✅ | ⚠️ 400 (tenant'sız) |
| Dashboard (`/api/dashboard`) | ✅ tam | ✅ (para yok) | ✅ (para+borç) | ✅ (kendi) | ❌ 400 |
| Tenant adı oku/düzenle (`/api/tenant`) | ✅ oku+yaz | ✅ oku | ✅ oku | ✅ oku | ❌ |
| **Platform tenant yönetimi (`/api/platform/**`)** | ❌ | ❌ | ❌ | ❌ | **✅** |

> **Genel ilke:** FRONTDESK = parayı görmez. FRONTDESK_ACCOUNTING = parayı görür, maaş görmez. TEACHER = kendi yoklaması. ADMIN = tenant içi her şey. **SUPER_ADMIN = platform sahibi: yalnız tenant yönetimi, iş verisine fail-closed izole (400/403).**

---

## 9. Web Frontend — TAMAMLANDI ✅ (iş modülleri + platform konsolu)

`web/` klasöründe **tüm modüller canlı + SUPER_ADMIN platform konsolu** ayrı ağaçta.

**İskelet/altyapı:** Vite+React+TS+Tailwind, keycloak-js (login-required, PKCE S256, token bellekte, otomatik refresh), `api/client` (axios: Bearer + ApiResponse açma + 401 yenileme).

**Tasarım sistemi:** `design-reference.html` (repo kökü, **resmî kaynak**) → erik+ahududu paleti + Fraunces (başlık) + Manrope (gövde) + `.card/.data-table/.badge/.tabs/.btn*`. Yeni tema uydurulmaz.

**Mimari:** `AuthContext` (`realm_access.roles` → `hasRole`/`hasAnyRole`, token `name` claim'i konsol kimliği için), `AppShell` (iş kullanıcıları), `ProtectedRoute`/`RoleRoute` + rol bazlı landing. Kalıp `web/.claude/skills/frontend-architecture/SKILL.md`'de.

**İş modülleri (liste/form/detay + rol gating):** Öğrenci · Tanımlar · Gruplar/Kayıt · Program/Yoklama · Finans · Hakediş · Stok/Satış · Raporlar · Kullanıcı Yönetimi · Profil. **Dashboard (Genel Bakış)**: role göre dolu panel (`.stat` + recharts trend + son hareketler + bugünkü dersler; `GET /api/dashboard`). İlk-şifre kilidi AppShell layout seviyesinde (bypass imkânsız).

**✅ YENİ web işleri (bu faz):** (a) **Grup Değiştir** — GroupDetailPage kayıt satırında, hedef GRUP dropdown + eski/yeni aidat **fark**ı gösteren onay modalı → `/transfer`. (b) **Öğretmen çoklu hakediş (Model C)** — TeacherForm'da `useFieldArray` ile "+" tip ekle/sil + tip başına değer inputu; GroupForm'da **Hakediş Tipi** dropdown (grup-tipinden varsayılan, düzenlenebilir); payout/rapor ekranları **liste-response**a uyarlandı. (c) **Logo** — amblem sidebar/konsol/ilk-parola + favicon (`web/src/assets/`).

**✅ SUPER_ADMIN Platform Konsolu (YENİ):**
- **Ayrı PlatformApp ağacı:** Login sonrası `hasRole('SUPER_ADMIN')` → `/platform/*`, **AppShell HİÇ render edilmez**. İş kullanıcısı `/platform/*` → 403. super.admin iş route'larına → redirect.
- **PlatformShell:** sidebar'sız sade konsol (üstte "Platform Konsolu" + kimlik token'dan + Çıkış). Tenant adı GÖSTERMEZ (super.admin'in tenant'ı yok). ⚠️ `/api/me`'ye BAĞIMLI DEĞİL — super.admin'de `/api/me` 400 döner, kimlik token'dan (`preferred_username`/`name`).
- **Tenant listesi (`/platform/tenants`):** `.data-table` (Ad/Status/Oluşturulma/Aksiyon), tabs (Hepsi/Aktif/Askıda) + debounce arama. Satır aksiyonu: Askıya Al (onaylı) / Aktif Et → PATCH /status.
- **Tenant oluştur formu:** RHF+Zod (ad+adminEmail+adminAd+adminSoyad), `error.fields`→input altı, 409→form üstü. Başarı → yeşil banner (username + ilk parola Artademi2026!); `warning` → amber banner (admin yaratılamadı, elle ekle). Her iki durumda tenant listede.
- **Dosyalar:** `api/platform.ts`, `features/platform/{usePlatformTenants,tenantSchema,PlatformShell,TenantListPage,TenantForm}.tsx`, `App.tsx` (rol çatallanması), `AuthContext.tsx` (name claim).

- **Logo varyantları** `web/src/assets/`: `artademi-logo-full.png` (login/Keycloak teması), `artademi-amblem.png` (sidebar/konsol), `artademi-favicon.png` (sekme). Landing kopyaları `infra/landing/assets/`.

---

## 10. Çalışma Yöntemi (yeni pencerede aynen kullanılacak)

### job.md yöntemi
Görev `job.md`'ye yazılır (gitignore'da), Claude Code'a "job.md dosyasını oku ve uygula" denir.

> **NOT:** `ARTADEMI_HANDOFF.md` artık repoda **tracked** (private repo; içinde test parolaları var). Claude Code diskten okuyup güncelleyebilir. (Üretim/devir notları `infra/DEPLOY-REHBERI.md`'de.)

### module-workflow skill (backend — KURULU)
Modül kurulduktan sonra Claude Code KENDİSİ doğrular: `./mvnw test` + backend restart + curl (mutlu yol + hata). **COMMIT/PUSH YAPMAZ.**

### Skiller
Backend `.claude/skills/`: `multi-tenancy`, `testing-standards`, `keycloak-auth`, `api-contract`, `project-architecture`, `spring-boot-backend`. Frontend `web/.claude/skills/frontend-architecture/SKILL.md`.

### Commit disiplini
Her commit öncesi `git status` ile sır dosyası (`.env`) kontrolü. Test yeşil olmadan commit yok.

---

## 11. Git Commit Geçmişi (son durum, hepsi origin/main'de)

```
... feat(report) 15fd04f → fix(teacher) + verify-roles.sh
→ [user + tenant modülleri]
→ feat(platform) aa2b65d (SUPER_ADMIN tenant CRUD)
→ feat(platform) d9d7a45 (ASKIDA login engeli)
→ feat(platform) [provisioning] (tenant + ilk ADMIN)
→ feat(web/platform) 23486c3 (SUPER_ADMIN konsolu)
→ feat(platform) 17b99e0 (subscription + grace/ASKIDA, V14)
→ feat(teacher) [/api/groups/mine]
→ feat(web) [logo yerleştirme]
→ feat(dashboard) [GET /api/dashboard]
→ feat(web) 8b46a87 (dashboard frontend, recharts)
→ feat(infra/keycloak) 3205947 (login teması)
→ feat(infra) (prod deploy: compose.prod + Dockerfile + Caddy)
→ feat(platform) (tenant kullanıcı CRUD + soft-delete/SILINDI + landing içeriği)
→ feat(infra) edf211f (artademi.com landing: Caddy file_server + www→apex)
→ fix(security) 61e4a1a (CORS allowed-origins env-driven — prod 403 çözümü)
→ feat(enrollment) 82dd48f (öğrenci grup transferi + otomatik aidat farkı, İş A)
→ feat(teacher,payout) a62ade4 (çoklu hakediş tipi — Model C grup-bazında, V15+V16, İş B)
```

> **PROD CANLI (Hetzner 37.27.241.117):** app.artademi.com (web+API) + auth.artademi.com (Keycloak) + **artademi.com/www landing** — hepsi SSL'li (Caddy/Let's Encrypt, Cloudflare DNS-only). Prod DB **Flyway v16**. Tek tenant: **Lina Sanat Merkezi** (`1111…`, AKTIF) + super.admin; Lina'da 3 öğretmen (hepsi SAATLIK, teacher_hakedis'e göç edildi). Test tenant'lar (test/test2/Tab Sanat) **kalıcı silindi**. Platform 403 zinciri (Security eski-imaj + provisioning SA-rolleri + CORS) **tamamen çözüldü**.

---

## 12. Dev DB Test Verisi (tenant A `11111111-...` = Lina)

- **Öğrenciler:** Ada Yılmaz(1, AKTIF, anne TC 98765432109), Mert(2, kardeş), Zeynep(3), Elif(4), Ahmet(5)
- **Branş:** Bale(1). **Salon:** Salon A(1, kap. 20). **Öğretmen:** Selin Aydın(1, SAATLIK 350, keycloakUserId=teacher.a sub).
- **Gruplar:** "Bale Başlangıç Cumartesi"(1, GRUP, aidat 1500) + "Selin ile Özel Bale"(2, OZEL, 500). **Kayıt:** Ada→grup1 AKTIF.
- **Program:** grup1 Cumartesi 11:00-13:00. **Finance:** Ada bakiye 1620.50; gider 200. **Ürün:** Mayo(1). **Payout:** Selin 2026-06 ODENDI 350.
- **Tenant B (Anka `2222…`):** "B-" önekli örnek veri zinciri (izolasyon testi).
- **Platform testlerinden kalan:** "Prov Test …" + "Warn …" tenant'ları + `yonetici…` admin'i dev Keycloak/DB'de (silme yok ilkesi).

---

## 13. SIRADAKİ İŞ: Yapılacaklar

### 13.0 REKABET ANALİZİ SONRASI YOL HARİTASI (2026-09-01)

**Rakip:** [derslic.com.tr](https://derslic.com.tr/) — kurs/etüt merkezleri, sanat kursları, pilates stüdyoları. Bulut tabanlı, **yalnız web** (mobil uygulama YOK, SSS'de teyitli). Fiyat kademeli: 1.750 / 2.250 / 2.750 / 3.500 TL (öğrenci sayısına göre, **KDV dahil**), 15 gün demo, yıllıkta 3 ay hediye.

**En kritik bulgu:** eski fiyatımız (5.000 TL + KDV = 6.000) rakibin giriş kademesinin ~3,4 katıydı; küçük kurumu daha demoya girmeden eliyordu. Yeni fiyat 2.000 + KDV = 2.400 — Derslic'in giriş paketinin (1.750) hâlâ bir miktar üstünde ama 300+ öğrencili kurumlarda artık biz ucuzuz.

#### ✅ Bu turda yapılanlar
- **Fiyat düşürüldü: aylık 2.000 TL + KDV, TEK PLAN sabit.** Kademe YOK, yıllık plan YOK.
  - ⚠️ **Yıllık plan bir ara eklenip GERİ ALINDI (2026-09-01, aynı gün).** `AbonelikPeriyodu`, `GET /api/billing/plans`, `PlanSecenegi`, periyot seçici — hepsi kaldırıldı. Tekrar istenirse git geçmişinde var; ama iyzico'da **her dönem AYRI plandır**, o yüzden yıllık için ikinci bir plan referansı (`IYZICO_YILLIK_PLAN_REF`) gerekir.
  - ⚠️ `BillingProperties.aylikPlanUcreti()` varsayılanı **10.000'di** (yml 5.000 derken) — bayat değer, 2.000'e çekildi.
  - Landing: fiyat kartı + Mesafeli Satış Sözleşmesi md.3 ve md.6 güncellendi.
- **iyzico canlı plan açıldı (2026-09-01):** ürün "Artademi Tam Paket" (`affb14cb-6b90-42a1-b291-c673cc4f8bab`) altında yeni plan **"Aylik Tam Paket 2000"** → `IYZICO_PLAN_REF=8a914fbf-61fb-4b5f-9c09-587a8a0c88bb`. `.env.prod` güncellendi (`BILLING_AYLIK_UCRET=2000` da), yedek: `.env.prod.yedek-20260901-174046`.
  - Canlıda duran eski planlar (abonesi YOK, temizlenebilir): "Aylik Tam Paket 5000" `e2902022-…`, "Aylik Tam Paket" 10.000 `1f2153d4-…`, "TEST 1 TL - silinecek" `a4953332-…`.
  - ⚠️ **iyzico'da plan fiyatı sonradan DEĞİŞTİRİLEMEZ**; yeni fiyat = yeni plan. Mevcut aboneler eski planda kalır (şu an abone yok, sorun değil).
- ⚠️ **YENİ TUZAK — iyzico imzası query string İÇERMEZ** (canlı API'de ölçüldü): `hex(HmacSHA256(rnd + uriPath + body, secret))` hesabında `?page=1&count=100` gibi bir query imzaya girerse **"Authentication token is not verified" (errorCode 8)** döner. `IyzicoAuth` javadoc'u tam tersini söylüyordu, düzeltildi. Bugünkü çağrıların hiçbirinde query yok; query'li bir uç eklenirse imza `path.split("?")[0]` ile hesaplanmalı.
- `scripts/iyzico-plan-olustur.py` yeniden yazıldı: ürünü **bul-ya-da-oluştur** (canlıda ürün zaten var, eski hâli "zaten var" hatasıyla duruyordu), imza query'siz, tek aylık plan açar.
- **Şube modülü yapıldı** (§7.18) — landing "çok şube" diyordu, kodda karşılığı yoktu.

#### ⏳ SONRAKİ İŞLER (rakip paritesi — öncelik sırasıyla)
| # | Modül | Durum / not |
|---|---|---|
| 1 | ~~**Makbuz / PDF çıktısı**~~ | ✅ **TAMAM** (2026-09-02) — tahsilat makbuzu + öğrenci kayıt formu, gömülü Türkçe font. Bkz. §7.19. |
| 2 | **SMS** | §13.2b'de planlı. Önkoşul: **şifreli tenant-bazlı ayar saklama** (iyzico tek anahtarla `.env`'de; SMS her kurumun kendi kimlik bilgisini ister). |
| 3 | **Otomatik bildirim** | Borç hatırlatma bugün ELLE (`BorcHatirlatmaPage`). Eklenecek: zamanlanmış gönderim (kurum opt-in), devamsızlık bildirimi, haftalık finansal özet. |
| 4 | ~~**Online ön kayıt formu**~~ | ✅ **TAMAM** (2026-09-07) — public form (slug) + başvuru listesi + öğrenciye dönüştürme. Bkz. §7.20. |
| 5 | **Kasa yönetimi** | Çoklu kasa/banka; tahsilat ve gider kasaya bağlanır, kasa bakiyesi + devir. |
| 6 | **Tedarikçi/cari** | Gider → tedarikçi ilişkisi, tedarikçi bakiyesi. |
| 7 | **Telafi dersi** | `YoklamaDurumu` bugün yalnız `GELDI/GELMEDI/IZINLI`. Telafi hakkı + kullanım takibi. |
| 8 | **Ders paketi / kontör** | `Group` bugün `aylik_aidat` + `ders_basi_ucret` taşıyor; "10 derslik paket + kalan ders" üçüncü model olarak yok. |
| 9 | **Veliden kartla tahsilat** | ⚠️ **ÖNCE HUKUK, SONRA KOD.** Parayı biz toplayıp kuruma aktarırsak bu ödeme aracılığıdır ve lisans sorusu doğurur; kurumun kendi alt üye işyeri (submerchant) hesabıyla yapılırsa iyzico ile ayrı sözleşme modeli gerekir. Mali müşavir/avukata sorulmadan başlanmamalı. Bugünkü iyzico entegrasyonu YALNIZCA kurumun BİZE ödediği abonelik içindir. |
| 10 | **Yıllık ödeme avantajı** | ❌ **İPTAL** (2026-09-01, Sercan kararı): tek sabit aylık fiyat tercih edildi. Rakip yıllıkta 3 ay hediye veriyor — pazarlama gerekçesi doğarsa yeniden değerlendirilir. |

#### 🎯 Rakipte de OLMAYAN (fark yaratacaklar)
- **Veli portalı** — veli kendi çocuğunun devamsızlık/borç/programını görür. Ne bizde ne onlarda; ilk yapan öne geçer.
- Uygulama içi bildirim merkezi · Mobil uygulama (React Native, planlı).

#### Bizim zaten üstün olduğumuz yerler (pazarlamada öne çıkar)
Çoklu hakediş (saatlik + ciro oranı aynı anda, grup bazında) · grup transferinde otomatik aidat farkı · kardeş eşleştirme · tip düzeyinde veri gizleme (ön büroya para alanları HİÇ gönderilmez) · işlem kaydı · KVKK veri dışa aktarma · devamsızlık + doluluk raporları · otomatik aylık tahakkuk · stok/ürün satışı.

#### Kod olmayan işler
Yardım videoları · WhatsApp destek hattı · rakibin 15 günlük demosunu açıp "bilinmiyor" işaretli özellikleri (veli portalı, raporlama derinliği, hakediş modeli) doğrulamak.

### 13.1 ✅ TAMAMLANDI (bu faz)
- **Platform fazı:** Tenant CRUD + ASKIDA login engeli + admin provisioning + web konsolu. SUPER_ADMIN = platform sahibi, iş modüllerine fail-closed, yalnız `/api/platform/**`.
- **Platform konsolu tam:** tenant kullanıcı CRUD (ekle/sil) + **soft-delete (SILINDI)** (§7.15).
- **İş A — öğrenci grup transferi** (§7.5) + **İş B — Model C çoklu hakediş** (§7.3/7.4/7.10), V15+V16, 205 test, prod'da canlı.
- **Prod CANLI + 403 zinciri çözüldü:** app/auth/landing SSL'li yayında; Security(eski-imaj)+provisioning(SA-rolleri)+CORS 403'leri çözüldü (bkz. §11 prod notu, §7.15 CORS).
- **Landing (artademi.com):** Caddy file_server, www→apex 301, logolar bağlı; animasyonlu hero + fiyatlandırma (4.000 TL/ay) + KVKK + iletişim (mailto info@artademi.com). ⚠️ Fiyat o gün 4.000 TL'ydi; GÜNCEL fiyat için §13.0.

### 13.2 KALAN BÜYÜK FAZ (subscription parasallaşması + bildirim)
> Hedef: ürün online abonelikle satılır. ⚠️ **GÜNCEL FİYAT: aylık 2.000 TL + KDV / yıllık 20.000 TL + KDV (bkz. §13.0)** — aşağıdaki 4.000/10.000 rakamları TARİHSELDİR. Kurum satın alır → login → ilk parola ile girer.
- **Ödeme entegrasyonu — BACKEND TAMAM (2026-07, V17):** iyzico Abonelik API adaptörü (`com.artademi.billing`): `GET /api/billing/subscription` + `POST /api/billing/checkout` (ADMIN), `POST /api/billing/callback` (iyzico 302), `POST /api/webhooks/iyzico` (HMAC imzalı, idempotent, fail-closed). `/api/billing/**` TenantStatus muaf (ASKIDA kurum ödeme yapabilir). Env: `IYZICO_API_KEY/SECRET_KEY/MERCHANT_ID/PLAN_REF` (boşken checkout 409, webhook 401). Araştırma raporu `docs/odeme-aracisi-arastirmasi-2026-07.md`. **Web Abonelik sayfası CANLI:** `/abonelik` (ADMIN; menü "Sistem→Abonelik") — özet kartı + RHF/Zod fatura formu + iyzico checkout embed (`IyzicoCheckoutForm` script'leri elle kurar) + `?sonuc=` banner. Compose: `BILLING_WEB_RETURN_URL`, `IYZICO_*` env. **iyzico SANDBOX HAZIR (2026-07-29):** Abonelik modülü destek talebiyle aktifleştirildi (panelde self-servis YOK — entegrasyon@iyzico.com'a üye işyeri no ile yazılır). Merchant ID **3431492**. API'den kurulan ürün "Artademi Tam Paket" + plan "Aylık Tam Paket" (10.000 TL/ay TRY, RECURRING) → `IYZICO_PLAN_REF=ddf664c2-22fb-456d-af7e-cdf5e1c65453`. Anahtarlar `.env.prod`'da (git'te YOK). ⚠️ **Gerçek yanıt sapmaları (canlı testte bulundu, koda işlendi):** `initialize` token'ı KÖKTE döner (data altında değil); ödeme tamamlanmadan sorgulanırsa `failure/201601` döner → istisna değil "başarısız sonuç" sayılır. Webhook imzası doküman ile teyitli: `hex(HmacSHA256(merchantId+secretKey+eventType+subRef+orderRef+custRef, secretKey))`. ✅ **SANDBOX UÇTAN UCA GEÇTİ (2026-07-31):** app.artademi.com/abonelik → iyzico formu → test kartı (5528 7900 0000 0008) → abonelik başladı. Webhook imzası canlı doğrulandı (geçerli→200, sahte→401). ⚠️ **Telefon tuzağı:** iyzico `gsmNumber` için YALNIZCA `+90XXXXXXXXXX` kabul eder (`0555…`/`555…`/`90555…` → HTTP 422); `TurkishPhone.toE164` bunu çevirir. ⚠️ Adaptör 4xx/5xx'i yutup gövdeyi okur — aksi halde iyzico hataları opak 500 olurdu. ⚠️ **WEBHOOK SANDBOX'TA TESLİM EDİLMİYOR (ölçüldü):** URL İşyeri Bildirimleri'ne kaydedildiği halde, başarılı tahsilata rağmen `billing_event`'e hiçbir kayıt düşmedi. → **MUTABAKAT (reconciliation) eklendi ve artık DOĞRULUK KAYNAĞI odur:** `BillingReconciliationService.reconcileAll(today)` sağlayıcıya "bu aboneliğin durumu ne?" diye sorar (`GET /v2/subscription/subscriptions/{ref}` → `subscriptionStatus` + `orders[].orderStatus/endPeriod`), kaçan tahsilatı yakalar ve `markPaid` ile dönemi ilerletir. `SubscriptionScheduler` her gün 03:00'te **önce mutabakat, sonra evaluate** çalıştırır (ters sıra ödeme yapan kurumu haksız yere askıya alırdı). Sağlayıcı sorgulanamazsa kayda DOKUNULMAZ (fail-safe); bir aboneliğin hatası diğerlerini durdurmaz. **KALAN:** webhook teslimi için iyzico'ya sorulacak (opsiyonel — mutabakat olmadan da sistem doğru çalışır) + canlı (production) merchant başvurusu.
- **Lead/iletişim formu — TAMAM (2026-07):** `POST /api/public/leads` (JWT'siz, honeypot+30sn IP cooldown) → Gmail SMTP ile info@artademi.com'a mail (`SMTP_USERNAME/SMTP_PASSWORD` app-password, `.env.prod`'da). Landing formu fetch ile bağlı (mailto kaldırıldı). Mail health check kapalı (`management.health.mail.enabled=false`). info@artademi.com = Google Workspace grubu (MX/SPF/DKIM Cloudflare'de, doğrulandı).
- **Platform ops dashboard (SUPER_ADMIN) — ADIM 1 TAMAM (2026-07-31):** `GET /api/platform/dashboard` + web `/platform` (konsolun yeni açılışı) — kurum/abonelik sayıları, **MRR** (yalnız AKTIF+AYLIK+ODENDI sayılır; deneme/grace/SILINDI gelire yazılmaz), dikkat gerektirenler (grace/başarısız/askıda), 7 günlük yaklaşan yenilemeler, son ödeme hareketleri (`billing_event`). Konsola sekme navigasyonu eklendi (`PlatformShell.SEKMELER` — yeni ops sayfaları oraya). MRR fiyatı `BILLING_AYLIK_UCRET` (varsayılan artık **2.000**; bkz. §13.0). **ADIM 2 TAMAM:** `GET /api/platform/billing/subscriptions?filtre=&q=` (iş-dili filtreler: ODEYEN/DENEME/GECIKMIS/ASKIDA/HEPSI; SILINDI yalnız HEPSI'de) + `GET /api/platform/billing/events?page=&size=` (sayfalı, PageMeta) → web `/platform/odemeler` sekmesi: kurum bazlı ödeme durumu tablosu + ham hareket listesi. **ADIM 3 TAMAM — denetim izi (V18 `platform_audit`):** kurum aç/durum değiştir/sil, kullanıcı ekle/sil, abonelik güncelle işlemleri iz bırakır. `GET /api/platform/audit` (sayfalı) → web `/platform/denetim`. ⚠️ Tasarım: entity **salt-yazılır** (setter YOK), `target_ad` **snapshot** (kurum silinse de iz okunur), kurum işlemlerinde iz **aynı transaction'da** yazılır (izsiz işlem olmasın); Keycloak'a giden kullanıcı işlemlerinde ise işlem başarılı olduktan SONRA yazılır (`kaydetBagimsiz`). Actor JWT `preferred_username`'den, yoksa "sistem". Aynı duruma tekrar PATCH iz YAZMAZ (gürültü yok).
- ✅ **Ödeme hatırlatma mailleri TAMAM (V19, 2026-08):** `BillingNotificationService` — 4 uyarı tipi (ODEME_BASARISIZ / GRACE_BASLADI / GRACE_BITIYOR (son 3 gün) / ASKIYA_ALINDI), kurumun **ADMIN** rolündeki kullanıcılarına (Keycloak'tan) gider. ⚠️ **Idempotency:** `uq_billing_notification(subscription_id, tip, donem_anahtari)` — scheduler her gün çalışır, aynı uyarı bir DÖNEM içinde tek kez gider; sonraki dönemde yeniden gidebilir. Alıcı yoksa iz YAZILMAZ (yönetici eklenince gitsin). Scheduler sırası: mutabakat → evaluate → **bildirim** (geçişlerden SONRA ki güncel durum yazılsın). Mail/Keycloak hatası günlük işi durdurmaz.
- **Kalan mail işleri:** (a) provisioning'de yeni admin'e kullanıcı adı + ilk parola maili; (b) Keycloak SMTP (forgot-password akışı kurulu ama mail gitmiyor).
- **Şifremi unuttum:** Keycloak forgot-password akışı + tema HAZIR; gerçek çalışması SMTP'ye bağlı (yukarıdaki mail işi).
- **Grace uyarı banner:** dashboard ADMIN'de `subscriptionWarning` gösteriliyor (kısmi); diğer rol/sayfalara yaygınlaştırma opsiyonel.

### 13.2b SMS ENTEGRASYONU (planlandı, 2026-08-30 — henüz YAPILMADI)

> Karar: SMS **kurum kendi sağlayıcı hesabını bağlar**, platform hesabından gönderilmez.

**Neden bu model** (e-posta itibar dersinin doğrudan sonucu):
- **İtibar paylaşılmaz** — ortak gönderici başlığında bir okulun kötü kullanımı diğerlerinin
  mesajlarını da riske atar. E-postada alan adımız zaten ortak; SMS'te aynı hatayı yapmayalım.
- **Veli göndereni tanır** — başlık `TAB SANAT` olur, `ARTADEMI` değil. Tanınmayan başlıktan
  gelen "borcunuz var" mesajı hem işe yaramaz hem şikâyet toplar.
- **Maliyet ve hukuki sorumluluk doğru yerde** — veliyle sözleşme ilişkisi okulundur.

**⚠️ Türkiye'ye özgü iki engel (planı etkiler, baştan bilinmeli):**
1. **Gönderici başlığı tescili** — Türkiye'de rastgele isimle SMS atılamaz; başlık operatörde
   tescillenir, şirket evrakı ister, birkaç gün sürer. Okul "bugün bağlayıp bugün gönderemez";
   onboarding metninde bu söylenmeli.
2. **İYS (İleti Yönetim Sistemi)** — ticari elektronik iletide alıcı onayının İYS'ye kaydı
   zorunlu. Mevcut sözleşme ilişkisi kapsamındaki bilgilendirme için istisna var ama
   "okul → veliye borç hatırlatma" bu sınırın neresine düşer, **hukukçuya sorulmalı**.
   Uygulamaya geçmeden önce güncel mevzuat araştırılacak.

**Teknik plan (iyzico kalıbının aynısı):**
- `SmsSaglayici` portu + somut uygulamalar (Netgsm / İletimerkezi / Verimor vb.)
- ⚠️ **ÖN KOŞUL — kurum bazlı şifreli sır saklama:** iyzico'da tek anahtar var ve `.env`'de
  duruyor; SMS'te HER KURUMUN kendi API bilgisi olacak ve DB'ye yazılacak. Düz metin OLAMAZ.
  Bu altyapı parçası SMS'ten ÖNCE yapılmalı.

**Önerilen sıra:** (1) sağlayıcı araştırması + İYS netleştirmesi → (2) şifreli kurum-bazlı
yapılandırma → (3) SMS gönderimi.

### 13.3 Küçük açık işler / opsiyonel
- Finans inline formlarını RHF+Zod'a hizalama (opsiyonel; kabul edilmiş istisna).
- Demo modülü (V2 `demo_note`) temizliği (opsiyonel).

---

## 14. Bilinen Eksikler / Teknik Borç

### ✅ 14.0 İLK-PAROLA ZİNCİRİ — KAPATILDI (tespit 2026-08-10, düzeltme 2026-08-29)

> Otovers'ta aynı konu çözülürken çapraz tespit edilmişti; iki açık da kapatıldı. 622 test yeşil.
>
> **(a) Sabit ortak parola KALDIRILDI.** Artık: e-postası olan kullanıcıya parola HİÇ atanmaz —
> Keycloak'ın "parolanı belirle" bağlantısı gönderilir, kullanıcı kendi parolasını kurar. Böylece
> mailde, logda, yanıtta, yedekte hiçbir yerde düz metin parola bulunmaz. E-postası olmayan
> kullanıcıda `IlkParola.uret()` ile KULLANICIYA ÖZEL rastgele parola üretilir (14 hane, her
> sınıftan en az bir karakter garantili — düz rastgele çekim politikayı ihlal edebiliyordu) ve
> yönetici ekranında BİR KEZ gösterilir. Hoş geldin maili artık parola içermez.
>
> **(b) Sunucu tarafı yaptırım EKLENDİ.** `ParolaDegisikligiInterceptor` bayrak duruyorsa
> 403 `PASSWORD_CHANGE_REQUIRED` döner. Muaf uçlar yalnızca çıkış yolu (`/api/me`,
> `/api/me/change-password`) + kimliksiz uçlar + `/api/platform/**` (super.admin'in kilit ekranı
> yok, kilitlenirse çıkış yolu kalmaz). 30 sn TTL önbellek + parola değişiminde açık invalidasyon.
> ⚠️ Keycloak'a ulaşılamazsa **fail-open**: altyapı hatası çalışan kurumu durdurmamalı.
>
> **Mevcut hesaplar:** prod'daki üç hesap (ezgi, sercan, super.admin) DEMO/TEST hesabıdır;
> eski sabit parolada kalmaları risk oluşturmaz. İlk gerçek müşteri zaten yeni akıştan geçecek
> (parolasını kendisi belirleyecek). Yine de canlıya gerçek kullanıcı alınırken bu üç hesabın
> parolası yenilenmeli ya da hesaplar kapatılmalı.

**(a) Sabit ORTAK ilk parola — `Artademi2026!`**

`UserService.java:53`, `KeycloakTenantAdminProvisioner.java:31`, `KeycloakTenantUserAdmin.java:33`
— üçünde de aynı sabit. Her yeni kullanıcı **aynı** parolayla açılıyor (`temporary=false`).

Sonuç: bu parolayı bilen herkes, **açılmış ama henüz ilk girişini yapmamış herhangi bir
hesaba** girebilir. Kullanıcı adları tahmin edilebilir olduğu için pratikte istismar edilebilir.
Parola ayrıca depoda yazılı ve hoş geldin mailinde düz metin gidiyor (`HosGeldinMaili`) —
gelen kutusunda, yedeklerde ve iletilmiş maillerde kalıcı olarak durur.

Bu, Otovers'ta 2026-08-09'da kapatılan açığın aynı sınıfı: orada sabit `operas123` vardı ve
ayrıcalık yükseltme zincirinin parçasıydı. Artademi'de rol ataması daha dar olduğu için etki
daha küçük, ama mekanizma aynı.

**Çözüm (Otovers'ta uygulanan):** parola **her kullanıcı için ayrı** üretilir, istemcide
(`crypto.getRandomValues`) — böylece hiçbir sunucu cevabında ve log satırında düz metin parola
bulunmaz — ve yöneticiye kayıttan sonra **bir kez** gösterilir. Üreteç realm parola politikasını
garanti etmeli: düz rastgele çekim, en az bir rakam/özel karakter garantisi vermediği için
Otovers'ta üretimlerin **%29,1'i** politikayı ihlal ediyordu.

**(b) `must_change_password` yalnızca İSTEMCİDE zorlanıyor**

Bayrak Keycloak özniteliğinde tutuluyor (doğru tercih — Keycloak'ın `UPDATE_PASSWORD` zorunlu
eylemi Direct Access Grant'i kırar, ileride mobil eklenirse bu önemli). **Ama yaptırım yok:**
`web/src/components/AppShell.tsx:33` bayrağı görünce yalnız kilit ekranını render ediyor;
backend'de kontrol eden hiçbir filtre/interceptor yok (`TenantFilter`,
`TenantStatusInterceptor`, `RequireTenantInterceptor`, `TenantAuditInterceptor` — dördünde de
geçmiyor).

Yani bu bir güvenlik kontrolü değil, **UX dürtmesi**. İsteği doğrudan API'ye atan biri
parolasını hiç değiştirmeden her şeye erişir — ki (a) yüzünden o parola zaten herkesin bildiği
sabit parola.

**Çözüm (Otovers'ta uygulanan):** sunucu tarafı tek kapı. `PasswordChangeRequiredFilter`
bayrak duruyorsa `403 {"code":"PASSWORD_CHANGE_REQUIRED"}` döner; muaf uçlar yalnızca
kullanıcının bu durumdan çıkabilmesi için gerekenler (profil oku, parola değiştir, menü,
çıkış). Öznitelik Keycloak admin API'sinden okunduğu için 30 saniyelik TTL'li cache +
parola değişiminde açık invalidasyon kullanıldı.

**(c) Not — dil tuzağı Artademi'de YOK, sebebi kayda değer**

Otovers'ta Keycloak'ın şifre sıfırlama maili İngilizce gitti: Keycloak dili tarayıcının
`Accept-Language` başlığından seçiyor ve kullanıcıda `locale` özniteliği yoksa realm varsayılanı
(`tr`) devreye girmiyor. Artademi bu tuzağa düşmüyor çünkü **maillerini Keycloak'a bırakmıyor**,
`HosGeldinMaili` gibi kendi Türkçe şablonlarını `JavaMailSender` ile gönderiyor. İleride
Keycloak'ın kendi maillerine (örn. `execute-actions-email`) geçilirse bu tuzak Artademi'de de
doğar; o zaman kullanıcıya `locale=tr` özniteliği yazılmalı ya da realm'den `en` kaldırılmalı.

- ✅ **ÇÖZÜLDÜLER (artık açık iş değil):** TEACHER `/api/groups/mine`; platform 403 zinciri (Security eski-imaj + provisioning SA-rolleri + CORS prod origin); landing canlı; tenant izolasyonu kanıtlı; platform konsolu kullanıcı CRUD + soft-delete; Model C çoklu hakediş; grup transferi.
- **Gerçek ödeme entegrasyonu YOK** (PayTR/iyzico) — paymentStatus elle/`markPaid` ile set ediliyor (subscription temeli hazır).
- **Mail YOK (info@artademi.com / Zoho bekliyor):** provisioning'de yeni admin'e parola maili gitmez (username + `Artademi2026!` konsolda gösterilir, super.admin elle iletir); grace/ödeme bildirimi yok; Keycloak SMTP yok → forgot-password sayfası temalı ama mail göndermez.
- `user` modülü: servis-katmanı validasyonları `error.fields` doldurmaz (yalnız `message`); kullanıcı listesinde PageMeta yok.
- Finans inline formları RHF+Zod yerine `useState` (kabul edilmiş istisna). Demo modülü (V2 `demo_note`) hâlâ duruyor.
- "Herkes sadece kendi girdiğini düzeltir" ince yetkisi yok. Satış/ödeme iptal/iade yok.
- ⚠️ **Keycloak prod kurulumu kısmen elle:** service-account realm-management rolleri + user-profile attribute'ları realm export'a (`infra/artademi-realm.json`) işlendi (yeniden import getirir); ama temiz bir yeni ortam kurulumunda doğrulanmalı.
- ⚠️ **Junk tenant kalıcı silme** prod'da elle (psql + kcadm) yapılır — konsol "Sil" yalnız soft-delete (SILINDI).

---

## 15. Hızlı Hatırlatmalar

- Kod değişince backend'i yenile (`./mvnw compile` → devtools restart). "No static resource" = eski kod.
- Uygulanmış migration düzenlenmez. Para = BigDecimal, asla double.
- Tenant-aware entity'de `findScopedById`, asla `findById`. **AMA** `Tenant` entity (platform) TenantAware DEĞİL → orada `findById` doğru.
- Kullanıcı/provisioning Keycloak Admin API ile (service account, §4) — frontend'den asla. Keycloak PUT tam-temsil ister (merge şart).
- **Lina (tenant A) ASKIDA'ya alınmaz** — ana dev tenant; askıya alma testleri Anka/yan tenant'larla.
- super.admin: tenant'sız, iş uçlarına 400, yalnız `/api/platform/**`; web'de ayrı PlatformApp ağacı (AppShell render edilmez).
- Tek mesaj = tek istek (kullanıcı tercihi).
- **DENEME→AKTİF otomatik DEĞİL** (ürün kararı, §7.26). "Aktif listede çıkmıyor" şikâyeti gelirse hata değil; uyarılar grup ekranı + Otomatik Tahakkuk'ta. Statü `PATCH /api/students/{id}/status`.
- ⚠️ **`formatDate` sadece `YYYY-MM-DD` içindir.** `Instant` alanı (`olusturulmaTarihi`, `createdAt` …) verirseniz ekranda `07T09:30:47.326471Z.09.2026` gibi bozuk metin çıkar — hata sessizdir, patlamaz. Instant için **`formatDateTime`** kullanın. (Bu tuzak üç kez ısırdı: TenantListPage ve DashboardPage call site'ta `.slice(0,10)` ile yamamıştı, Ön Kayıt listesinde canlıya çıktı. `formatDate` artık defansif ama doğru fonksiyonu seçmek yine de sizin işiniz.)
- **Deploy (2026-09-08'den itibaren normal `git pull`):**
  ```bash
  ssh root@37.27.241.117 "cd /opt/artademi && git pull && cd infra && \
    docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build backend web"
  ```
  Sunucuda **SSH deploy key** kurulu (`/root/.ssh/artademi_deploy`, `~/.ssh/config`'te github.com için tanımlı); remote `git@github.com:...`. Anahtar **salt-okunur** ve yalnızca bu depoya kapsamlı — sunucu ele geçirilse bile kod push'lanamaz.
- ⚠️ **Geçmiş tuzak (çözüldü, tekrarlarsa tanıyın):** HTTPS remote ile sunucu `git pull` yapamıyordu — `GET /info/refs` 200 dönerken nesneleri taşıyan `POST /git-upload-pack` **401** veriyordu (depo public olmasına rağmen); protokol v1'e düşürmek de çözmedi. Çözüm HTTPS'i onarmak değil **SSH'a geçmek** oldu. O dönemde deploy'lar `git bundle` ile yapıldı; artık gerekmiyor. Aynı belirti dönerse önce `ssh -T git@github.com` ile anahtarı doğrulayın.
- Deploy: compose **`infra/`** altındadır (`/opt/artademi/infra/docker-compose.prod.yml`), repo kökünde DEĞİL. Landing Caddy'den doğrudan servis edilir (pull yeterli), ama **panel ayrı bir `web` konteyneridir** — frontend değişikliği için `up -d --build web` şart.
