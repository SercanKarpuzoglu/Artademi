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
---

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

### 13.1 ✅ TAMAMLANDI (bu faz)
- **Platform fazı:** Tenant CRUD + ASKIDA login engeli + admin provisioning + web konsolu. SUPER_ADMIN = platform sahibi, iş modüllerine fail-closed, yalnız `/api/platform/**`.
- **Platform konsolu tam:** tenant kullanıcı CRUD (ekle/sil) + **soft-delete (SILINDI)** (§7.15).
- **İş A — öğrenci grup transferi** (§7.5) + **İş B — Model C çoklu hakediş** (§7.3/7.4/7.10), V15+V16, 205 test, prod'da canlı.
- **Prod CANLI + 403 zinciri çözüldü:** app/auth/landing SSL'li yayında; Security(eski-imaj)+provisioning(SA-rolleri)+CORS 403'leri çözüldü (bkz. §11 prod notu, §7.15 CORS).
- **Landing (artademi.com):** Caddy file_server, www→apex 301, logolar bağlı; animasyonlu hero + fiyatlandırma (4.000 TL/ay) + KVKK + iletişim (mailto info@artademi.com).

### 13.2 KALAN BÜYÜK FAZ (subscription parasallaşması + bildirim)
> Hedef: ürün online aylık abonelikle satılır (tek plan **4.000 TL/ay**, landing'de duyuruldu). Kurum satın alır → login → ilk parola ile girer.
- **Ödeme entegrasyonu (SIRADAKİ BÜYÜK İŞ):** **iyzico/PayTR** → subscription `paymentStatus`'u gerçek ödemeye bağlar (subscription temeli V14 HAZIR; `markPaid` ucu var). ⚠️ Sağlayıcı hesabı + sandbox anahtarları + webhook gerekir; Sercan kendi sandbox'ıyla test eder.
- **Mail / bildirim (info@artademi.com — Zoho):** (a) provisioning'de yeni admin'e kullanıcı adı + ilk parola maili; (b) grace başlangıcı/bitişi + ödeme hatırlatma. Şu an mail YOK (Keycloak SMTP de YOK → forgot-password akışı kurulu ama mail gitmiyor).
- **Şifremi unuttum:** Keycloak forgot-password akışı + tema HAZIR; gerçek çalışması SMTP'ye bağlı (yukarıdaki mail işi).
- **Grace uyarı banner:** dashboard ADMIN'de `subscriptionWarning` gösteriliyor (kısmi); diğer rol/sayfalara yaygınlaştırma opsiyonel.

### 13.3 Küçük açık işler / opsiyonel
- Finans inline formlarını RHF+Zod'a hizalama (opsiyonel; kabul edilmiş istisna).
- Demo modülü (V2 `demo_note`) temizliği (opsiyonel).

---

## 14. Bilinen Eksikler / Teknik Borç

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
