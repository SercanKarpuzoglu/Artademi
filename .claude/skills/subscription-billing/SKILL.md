---
name: subscription-billing
description: The platform-level subscription layer that monetizes the SaaS — tenant lifecycle (trial/active/suspended/cancelled), plans, recurring billing via iyzico/PayTR (Turkey), and access gating. Critically distinguishes platform revenue (schools paying us) from a tenant's own internal accounting. Consult this when working on tenants, plans, billing, or access control on suspension.
---

# Abonelik ve Faturalandırma (Platform Katmanı)

## En kritik ayrım
İki tür "para" vardır, ASLA karıştırılmaz:
1. **Platform geliri** — sanat okullarının (tenant) bize ödediği aylık abonelik. `subscription`, `plan`, `payment` (platform) tablolarında yaşar, tenant_id'nin ÜSTÜNDEdir.
2. **Tenant'ın iç muhasebesi** — okulun kendi öğrenci aidatları, hakedişleri, kasası. Bu, o tenant'ın iş verisidir (tenant_id taşır) ve abonelikle ilgisi yoktur.

Bir şey yazarken hep sor: bu, okulun bize ödediği abonelik mi (platform), yoksa okulun kendi içindeki para mı (tenant verisi)?

## Tenant yaşam döngüsü
- `TRIAL` — deneme süresi, tam erişim.
- `ACTIVE` — aboneliği güncel, tam erişim.
- `PAST_DUE` — ödeme alınamadı; kısa bir ek süre (grace) tanınır.
- `SUSPENDED` — erişim kapalı; kullanıcılar giriş yapsa da iş ekranlarına giremez. **Veri SİLİNMEZ.**
- `CANCELLED` — abonelik sonlandı; veri saklama politikasına göre tutulur.

## Erişim kontrolü (gating)
- Her isteğin tenant'ının durumu kontrol edilir. `SUSPENDED`/`CANCELLED` ise iş endpoint'leri `403` + `error.code = "TENANT_SUSPENDED"` döner; sadece "ödeme yap / aboneliği yenile" akışı açık kalır.
- Plan limitleri (varsa: öğrenci sayısı, kullanıcı sayısı, modül erişimi) burada uygulanır.

## Ödeme — Türkiye
- Tekrarlayan (recurring) tahsilat için **iyzico** veya **PayTR** (ikisi de TR'de abonelik/saklı kart destekler). Sağlayıcıyı soyutla: `PaymentProvider` arayüzü arkasında tut ki ileride değiştirilebilsin.
- Webhook ile ödeme sonucu alınır; başarılı → `ACTIVE`, başarısız → `PAST_DUE`→(grace sonu)→`SUSPENDED`.
- Kart verisi BİZDE saklanmaz; sağlayıcının token/saklı kart mekanizması kullanılır. PCI yükü sağlayıcıdadır.

## Yetki
- Bu katmanın tamamı `SUPER_ADMIN` (platform) içindir. Tenant kullanıcıları kendi aboneliklerinin durumunu görebilir ve ödeme yapabilir, ama plan/tenant yönetimi platforma aittir.

## Sıralama notu
Bu modül ürünleşme için şarttır ama ilk iş modüllerinden (öğrenci, yoklama, hakediş) SONRA hayata geçirilebilir. Yine de tenant ve durum alanları en baştan veri modelinde bulunur ki sonradan eklemek sancılı olmasın.
