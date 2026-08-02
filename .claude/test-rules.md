# Artademi — test kuralları

`parsius-core:test-author` bu dosyayı okur ve **evrensel kurallara + Spring stack
kurallarına ek olarak** uygular.

> Derivation guard kalıbı ve H2/Testcontainers ayrımı
> `parsius-spring:test-rules` skill'indedir. Burada **Artademi'ye özgü** durum var.

---

## Mevcut durum (2026-08-03 doğrulandı)

- **Backend: 33 `*Test.java`**, Testcontainers `pom.xml`'de tanımlı. Controller
  testleri yaygın (`AttendanceControllerTest`, `ScheduleControllerTest`,
  `GroupControllerTest` …).
- **Tenant izolasyon testi emsali var:** `DemoNoteTenantIsolationTest` — yeni
  izolasyon testi yazarken bundan kopyala.
- **Web: test altyapısı YOK.** `web/package.json`'da `test` script'i, Vitest veya
  React Testing Library **kurulu değil** (yalnız `dev`/`build`/`preview`).

## Web testi — önce altyapı

`testing-standards` skill'i Vitest + RTL tarif eder ama bu proje henüz kurmadı.
Web tarafına test istenirse:

1. Bunu **kurulum kararı** olarak bildir (Vitest + RTL + jsdom eklenecek).
2. Kurulum yapma — karar kullanıcınındır.
3. Kurulana kadar web testi **yazma**; yerine backend kapsamını güçlendir.

Var olmayan bir runner'a test yazmak sessizce ölü kod üretir.

## Zorunlu — tenant izolasyon testi

Her yeni tenant iş özelliği için: **"A tenant'ının verisi, B tenant bağlamında
istek atılınca görünmüyor"** testi.

Artademi'de izolasyon global Hibernate filtresine dayanıyor — ama filtre
**PK-find'ı kapsamaz** (`parsius-spring:review-rules` #6). Yani `findById`
kullanan bir yol açıldıysa izolasyon testi onu **mutlaka** kapsamalı; filtreye
güvenmek yetmez.

Emsal: `DemoNoteTenantIsolationTest`.

## Platform katmanı testleri

`platform/` (tenant, subscription, plan) tenant filtresinden **muaftır**. Bu
katmanın testi izolasyon değil, **yetki** doğrular: `SUPER_ADMIN` dışındaki bir rol
platform endpoint'ine erişemiyor mu?

Abonelik durumu testleri: `SUSPENDED` tenant'ın iş endpoint'lerinden `403` +
`TENANT_SUSPENDED` aldığı, ödeme akışının açık kaldığı.

## Sözleşme uyumu

Controller testleri yanıt **zarfını** da doğrulasın (`success`, `data`, `error`,
sayfalıda `meta`) — yalnız gövdeyi değil. Zarf sözleşmesi web tarafının bağlı
olduğu tek şey.

## Adlandırma

Mevcut kalıp: `<Sınıf>Test.java`, aynı paket ağacı altında `backend/src/test`.
