---
name: react-native-mobile
description: Mobile app patterns for React Native + Expo + TypeScript — navigation, screens, the shared API client/types with web, and TanStack Query. Consult this when building or changing the mobile app, which we ship for every project.
---

# React Native (Expo) Mobil Konvansiyonları

Her projenin mobil ayağı vardır. Mobil, web ile aynı API sözleşmesini kullanır; sadece sunum katmanı farklıdır.

## Yapı
- `api/` — web ile AYNI Axios sözleşmesi ve AYNI tipler. Tip ve endpoint mantığı tekrar yazılmaz; `shared/`'dan paylaşılır ya da birebir aynalanır.
- `navigation/` — React Navigation (stack + tab). Rota tipleri TS ile tiplenir.
- `features/<özellik>/` — ekranlar ve hook'lar, özelliğe göre.
- `components/` — paylaşılan UI bileşenleri.

## Veri ve durum
- Sunucu durumu TanStack Query (web ile aynı query key konvansiyonu).
- Token güvenli depoda (`expo-secure-store`), AsyncStorage'da düz metin token tutulmaz.
- Çevrimdışı/yeniden deneme: ağ hatasında kullanıcıya net mesaj, sessiz başarısızlık yok.

## Ekran ilkeleri
- Mobilin odağı saha işleri: hızlı, az dokunuşla biten akışlar (liste → detay → işlem).
- Ağır raporlama/toplu işlemler webde kalır; mobile zorlanmaz.
- Dokunma hedefleri yeterince büyük; yükleniyor/boş/hata durumları her ekranda.

## Web ile paylaşım kuralı
- Bir özellik backend'de değişince: tip (`shared/`), web `api/`, mobil `api/` üçü birlikte güncellenir.
- Yeni endpoint eklenince mobil tarafın da onu tüketip tüketmeyeceği `feature-builder` akışında kararlaştırılır.

## Yapı/derleme
- Expo yönetilen akış. Yerel ortam değişkenleri `app.config` üzerinden; sır gömülmez.
