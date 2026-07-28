# Ödeme Aracı Kurum Araştırması — Türkiye (2026-07)

> Artademi aylık abonelik tahsilatı (~10.000 TL/ay + KDV, kurumsal) için aracı kurum seçimi.
> Yöntem: çok kaynaklı web araştırması + iddia bazında 3-oylu çapraz doğrulama (24 kaynak, 22 onaylı bulgu).
> Tüm birincil kaynaklar (TCMB listeleri, sağlayıcı dokümanları) 26.07.2026'da canlı çekilip doğrulandı.

## SONUÇ: Birincil öneri **iyzico**, B planı **PayTR**

## iyzico — neden birincil

- **TCMB e-para lisanslı** (İyzi Ödeme ve Elektronik Para Hizmetleri A.Ş., kurum kodu 864; resmi listede doğrulandı).
- **Native Abonelik ürünü:** aylık (D/W/M/Y) planlar, kart saklama **iyzico tarafında** (bizde PCI yükü yok, `cardUserKey/cardToken`), ve **her tekrarlayan tahsilat için webhook** (`subscription.order.success` / `subscription.order.failure`). Yani kendi billing scheduler'ımızı yazmadan V14 Subscription'a bağlanır: webhook → `paymentStatus=ODENDI` → `evaluate()` zinciri.
  - Webhook URL panelden kaydedilir (Ayarlar → İşyeri Bildirimleri); 2xx dönene dek 15 dk arayla 3 deneme; webhook sonrası durumu API'den yeniden sorgulamak öneriliyor.
- **Maliyet:** liste komisyonu **%4,29 + 0,25 TL/işlem** (resmî fiyat sayfası; kurumsal pazarlığa açık). Abonelik eklentisi ilk 3 ay ücretsiz, sonra **199 TL** (dönem muhtemelen aylık; dokümanda net değil).
- **PayNet artık iyzico:** Paynet Ödeme Hizmetleri A.Ş. Aralık 2025'te iyzico tüzel kişiliğiyle birleşti (TCMB "faaliyet izni sona erenler" listesinde) — iyzico'nun B2B tahsilat tarafını güçlendiriyor.
- Kaynaklar: [docs.iyzico.com/en/products/subscription](https://docs.iyzico.com/en/products/subscription) · [docs.iyzico.com/en/advanced/webhook](https://docs.iyzico.com/en/advanced/webhook) · [iyzico fiyatlandırma](https://iyzico.com/destek/yardim-merkezi/genel-bilgiler/fiyatlandirma) · [TCMB e-para listesi](https://www.tcmb.gov.tr/wps/wcm/connect/tr/tcmb+tr/main+menu/temel+faaliyetler/odeme+hizmetleri/elektronik+para+kuruluslari)

## PayTR — B planı (koşullu)

- TCMB e-para lisanslı (kod 863). Kart saklama `utoken`+`ctoken` çiftiyle sağlam.
- **Fark:** recurring **merchant-triggered** — PayTR tarafında plan/zamanlama YOK. Aylık çekimi bizim backend tetikler (`@Scheduled` çekim motoru gerekir: `recurring_payment=1 + non_3d=1` POST).
- **Şart:** tekrarlayan çekimler Non3D işler → mağaza hesabına **Non3D/recurring yetkisi** onboarding'de mutlaka talep edilmeli.
- ⚠️ Piyasadaki "%2,19 + ertesi gün hesaba geçiş" söylemi doğrulamada **çürütüldü** (0-3): %2,19 yeni üye kampanya oranı, komisyon↔valör ters orantılı. Gerçek oran teklifle netleşir.
- Kaynak: [dev.paytr.com kart saklama/tekrarlayan ödeme](https://dev.paytr.com/en/direkt-api/kart-saklama-api/kayitli-kart-tekrarlayan-odeme)

## Elenenler / değerlendirilemeyenler

| Aday | Durum |
|---|---|
| **Sipay** | Teknik olarak EN zengin recurring API (Sipay tarafında plan zamanlama, 3DS recurring, halka açık sandbox — canlı test edildi). AMA TCMB Ekim+Kasım 2025'te sanal POS'un çekirdeği olan faaliyet izinlerini (6493 md. 12/1-b,c + 18/2) geçici durdurdu; mahkeme yürütmeyi durdurdu, dava 2026'da sürüyor. **Regülasyon riski nedeniyle şimdilik elendi** — dava lehine biterse yeniden aday. |
| **Craftgate** | Güçlü bağımsız kart saklama API'si + bakımlı Java SDK. AMA TCMB'nin hiçbir listesinde yok (muhtemelen lisanssız orkestrasyon katmanı); lisans/settlement zinciri netleşmeden tek başına aday değil. |
| **Stripe / PayPal** | Türkiye'de kurulu Ltd. şirkete **kapalı**. Stripe ülke listesinde TR yok, TR IBAN'a settlement yok (tek yol yabancı şirket kurmak). PayPal 2016'da BDDK reddiyle çıktı. |
| **PayNet** | iyzico ile birleşti — bağımsız aday değil. |
| **Param** | TCMB aktif listesinde, ancak onun da 2025'te e-para ihraç izni askıya alındı (süreç belirsiz); recurring yeteneği doğrulanamadı. |
| **Moka United / United Payment** | Nisan 2025'te birleştiler (tek kuruluş). Recurring/kart saklama yeteneği doğrulanamadı. |
| **Vallet** | TCMB listelerinde bulunamadı; değerlendirilemedi. |

## Önemli çekinceler

1. **Komisyon/settlement karşılaştırması eksik:** yalnız iyzico liste oranı birincil kaynakla doğrulandı. Nihai karar öncesi **Parsius Bilişim Ltd. Şti. adına iyzico VE PayTR'den yazılı teklif alınmalı** (10.000 TL/ay tek çekim kurumsal profil için pazarlık payı yüksek).
2. Ltd. şirket onboarding koşulları hiçbir aday için doğrulanmış veri üretmedi — başvuruda netleşecek.
3. iyzico 199 TL eklenti ücreti güncellenmiş olabilir (enflasyon).

## Entegrasyon planı (öneri)

1. **Sercan:** iyzico'ya Parsius Ltd. ile başvuru + sandbox hesabı; paralel PayTR teklifi (kıyas için).
2. **Backend:** `subscription-billing` fazı — iyzico Abonelik API entegrasyonu, webhook endpoint'i (`/api/platform/webhooks/iyzico` benzeri, imza doğrulamalı), webhook → `Subscription.paymentStatus` güncelleme (mevcut `markPaid`/`evaluate` altyapısı hazır, V14).
3. **Ödeme sayfası:** tenant admin'in kart bilgisi girip aboneliği başlattığı sayfa (iyzico checkout form/API).
4. B planına düşülürse: PayTR için `@Scheduled` çekim motoru + Non3D yetkisi.
