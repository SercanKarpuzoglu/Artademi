---
name: multi-tenancy
description: The tenant isolation model for this SaaS — shared schema with a tenant_id column, tenant context resolved from the JWT, and automatic per-query tenant filtering so no business data ever leaks between art schools. Consult this for EVERY entity, repository, query, and endpoint. This is the single most important rule in the codebase.
---

# Çok Kiracılı (Multi-Tenant) Mimari

Bu uygulama birden çok sanat okuluna (tenant) tek kurulumda hizmet veren bir SaaS'tır. Bir okulun verisi başka bir okula ASLA sızmamalıdır. Bu skill, bunu garanti eden kuralların tek kaynağıdır.

## İzolasyon modeli: Shared schema + tenant_id
- Tek veritabanı, tek şema. Her **iş tablosu** bir `tenant_id` kolonu taşır (öğrenci, veli, grup, ders, yoklama, ödeme, hakediş, ürün, stok, kasa hareketi... istisnasız hepsi).
- İstisnalar (tenant_id taşımayanlar): platform düzeyi tablolar — `tenant`, `subscription`, `plan` ve platform `super_admin` kullanıcıları. Bunlar tenant'ların ÜSTÜNDEdir (bkz. `subscription-billing`).

## Tenant bağlamı (TenantContext)
- Tenant kimliği yalnızca **JWT içindeki `tenant_id` claim**'inden okunur. İstemciden (URL, body, header parametresi) ASLA alınmaz — sahtelenebilir.
- Her istekte bir filtre/interceptor token'daki `tenant_id`'yi okur, `TenantContext`'e (ThreadLocal veya request scope) koyar, istek bitince temizler.
- Servis/repository katmanı tenant'ı `TenantContext`'ten alır.

## Otomatik filtreleme — "unutulamaz" olmalı
İnsan hatasına yer bırakmadan, her sorguya tenant koşulu **altyapı tarafından** eklenir:
- Tercih edilen yol: Hibernate `@Filter` / `@TenantId` veya tüm entity'lerin türediği bir `TenantAware` taban sınıfı + global bir Hibernate filtresi; filtre her oturumda `TenantContext`'teki değerle etkinleştirilir.
- Yazma (insert) sırasında `tenant_id`, `TenantContext`'ten otomatik set edilir; geliştirici elle yazmaz, yazsa bile bağlamdaki değerle doğrulanır.
- Sonuç: bir repository metodu `tenant_id` koşulunu yazmayı "unutsa" bile veri sızmaz, çünkü filtre global olarak aktiftir.

## PK find tenant sızıntısı (ZORUNLU)
Hibernate `@Filter`, `repository.findById(id)` / `EntityManager.find()` gibi PK-find çağrılarına UYGULANMAZ — yalnızca sorgulara (JPQL/Criteria) uygulanır. Bu nedenle `findById`, başka tenant'ın kaydını id ile döndürebilir = tenant sızıntısı.

KURAL: Tenant-aware (`TenantAware` türevi) hiçbir entity için `repository.findById` KULLANILMAZ. Bunun yerine tenant filtresine tabi bir sorgu kullan:
- JPQL: `@Query("select s from Student s where s.id = :id")` gibi bir `findScopedById`, VEYA
- Criteria/Specification ile id eşitliği.

Tekil getirme (get/update/changeStatus/detay) ve ilişki çekme dahil HER yerde bu geçerlidir. Bulunamazsa `NotFoundException` (404). Bu, izolasyonun "unutulamaz" olması için zorunludur ve demo modülünde fark edilmemişti (orada yalnızca `findAll` vardı, o da sorgu olduğu için filtreleniyordu).

## Demir kurallar
1. `tenant_id` istemciden gelmez; sadece JWT claim'inden. Controller hiçbir zaman tenant'ı parametre olarak kabul etmez.
2. Hiçbir iş sorgusu tenant filtresi olmadan çalışmaz. Toplu/raporlama sorguları dahil.
3. Bir kayda erişen her işlem (oku/güncelle/sil) önce kaydın `tenant_id`'sinin `TenantContext` ile eşleştiğini garanti eder. **`findById`/`find()` KULLANMA** — PK-find filtreye tabi değildir (bkz. "PK find tenant sızıntısı").
4. Platform düzeyi işlemler (tenant açma, abonelik) yalnızca `SUPER_ADMIN` içindir ve tenant filtresinden muaftır — bunlar ayrı, açıkça işaretli servislerde yaşar.
5. Test: her özellik için "A tenant'ının verisi B tenant'ı olarak istek atınca görünmüyor" testi yazılır (bkz. `testing-standards`).

## Veritabanı tarafı
- `tenant_id` her iş tablosunda `NOT NULL` ve indeksli (çoğu sorgu onunla filtrelendiği için bileşik indekslerin ilk kolonu genelde `tenant_id`).
- İleride güçlü izolasyon gerekirse PostgreSQL Row-Level Security (RLS) ile veritabanı seviyesinde de zorlanabilir; başlangıçta uygulama seviyesi filtre yeterli.
