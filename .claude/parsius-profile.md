<!-- parsius-profile v1 -->

# Artademi — Parsius proje profili

`parsius-core` ve `parsius-spring` bileşenleri proje-spesifik değerleri buradan
okur. Spec: `plugins/parsius-core/PROFILE-SPEC.md` (parsius-claude reposu).

**Boş alan tahmin edilmez** — alanı isteyen bileşen DURUR ve sorar.

## Komutlar
- build: `cd backend && ./mvnw -q compile`
- test: `cd backend && ./mvnw test`
- typecheck: `cd web && npx tsc --noEmit`
- lint:
  <!-- BOŞ — web/package.json'da lint script'i yok (yalnız dev/build/preview).
       ESLint kurulana kadar bu adım atlanır. -->

> `web` derlemesi zaten `tsc && vite build` — `build` script'i tip kontrolünü
> içerir. Ayrı `typecheck` hızlı geri bildirim için.

## Branch
- çalışma: `main`
- prod hedefi: `main`
- dokunma:

> **Tek branch.** otovers'taki `test` gibi atılabilir bir tampon yok — `main`'e
> push doğrudan tek dala gider. `auto-push` bu yüzden kapalı (aşağı bak).

## Git
- auto-push: false
- commit scope: `backend`, `web`, `infra`, `docs`, `claude`

> **Bilinçli olarak kapalı.** Üç gerekçe: (1) tek branch `main`, push için tampon
> yok; (2) projenin kendi `module-workflow` skill'i zaten *"COMMIT ETME, PUSH
> ETME — commit kararı kullanıcınındır"* diyor, bu yerleşik disiplin korunuyor;
> (3) commit yereldir ve geri alınabilir, push dışarı yayılır. Push daima açık
> onayla.

## Deploy
- mekanizma:
- tetikleyen:
- servisler:
- test URL:
- prod URL:
- kapsam kuralı:

> ⚠️ **Bilinçli boş.** Hetzner + Docker olduğu biliniyor ama somut mekanizma,
> servis adları ve URL'ler doğrulanmadı. Deploy gereken ilk turda skill **duracak
> ve soracak** — tahmin etmeyecek. Doğrulanınca burası doldurulur.

## Veri
- db: postgres
- erişim:
- test hedefi:
- prod hedefi:
- migration aracı: flyway

> ⚠️ **`erişim` ve hedefler bilinçli boş.** Postgres olduğu ve Flyway kullanıldığı
> (`backend/src/main/resources/db/migration/V<n>__*.sql`, V10–V14 mevcut)
> doğrulandı; bağlanma şekli (ssh+docker mu, lokal mi, port/kullanıcı) doğrulanmadı.
> `db-diagnose` benzeri bir iş gerektiğinde skill soracak.

## API
- envelope: wrapped

> `{ success, data, error, meta }` zarfı. Hata `code` makine-okur sabit
> (`NOT_FOUND`, `VALIDATION_ERROR`, `TENANT_SUSPENDED`, `TOKEN_EXPIRED` …),
> `message` kullanıcıya gösterilebilir Türkçe. Sayfalı listede `meta` dolu.

## Auth
- role prefix: `ROLE_`

> `JwtAuthenticationConverter` token rollerine **`ROLE_` ekler**
> (`ROLE_ADMIN`, `ROLE_FRONTDESK`, `ROLE_FRONTDESK_ACCOUNTING`, `ROLE_TEACHER`).
> Bu yüzden `hasRole`/`hasAnyRole` bu projede **doğru ve yerleşik** kullanımdır
> (kod tabanında 65 kullanım: 32 `hasAnyRole` + 33 `hasRole`). otovers'ın `hasRole` yasağı **buraya uygulanmaz** —
> bkz. `parsius-spring` README, "Neden yetki ifadesi kuralı KOŞULLU yazıldı".

Tenant kimliği JWT'deki `tenant_id` claim'inden; istemciden asla.

## Tasarım
- token prefix: `--`

## i18n
- var: false
- dosyalar:
- key konvansiyonu:

> Kullanıcıya görünen metinler doğrudan Türkçe. i18n katmanı yok — i18n kuralları
> N/A.

## Devir notu
- dosya: `ARTADEMI_HANDOFF.md`

## İnceleme
- ek kurallar: `.claude/review-rules.md`

## Test
- ek kurallar: `.claude/test-rules.md`

## Sunucular
- app (Hetzner): `ssh artademi` (root) — 37.27.241.117 · Ubuntu 24.04 · artademi-prod
