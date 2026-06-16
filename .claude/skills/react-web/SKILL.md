---
name: react-web
description: Web frontend patterns for React + TypeScript + Vite apps — folder layout, the Axios API client, TanStack Query for server state, components, and forms with React Hook Form + Zod. Consult this when adding or changing web UI or data fetching.
---

# React Web Konvansiyonları

## Yapı
- `api/` — Axios instance + endpoint fonksiyonları + TS tipleri. Bileşenler `fetch`/`axios` çağrısını doğrudan yapmaz; hep `api/` üzerinden.
- `features/<özellik>/` — o özelliğin bileşenleri ve hook'ları. Özelliğe göre gruplanır.
- `components/` — özelliğe bağlı olmayan paylaşılan/sunum bileşenleri (Button, Table, Modal).
- `lib/` — queryClient, yardımcılar, biçimlendiriciler.
- `routes/` — React Router rota tanımları.

## API istemcisi
- Tek bir Axios instance (`baseURL`, `Authorization` interceptor'ı).
- Response interceptor zarfı açar: başarılıysa `data.data`, `success:false` ise `data.error`'ı hata olarak fırlatır (bkz. `api-contract`).
- Her kaynak için tiplenmiş fonksiyon: `getStudents(params): Promise<Page<StudentResponse>>`.

## Sunucu durumu = TanStack Query
- Sunucudan gelen veri `useQuery`/`useMutation` ile yönetilir, elle `useState`+`useEffect` ile değil.
- Query key konvansiyonu: `['students', { status, page }]`.
- Mutation sonrası ilgili query `invalidateQueries` ile tazelenir.
- Yükleniyor / hata / boş durum her listede ele alınır (boş ekran kullanıcıyı yönlendirir, bkz. frontend yazım ilkesi).

## Formlar
- React Hook Form + Zod şeması. Zod şeması mümkünse backend doğrulamasını aynalar.
- Hata mesajları alan altında Türkçe gösterilir; submit sırasında buton kilitlenir.

## Bileşen ilkeleri
- Bileşenler küçük ve tek işli. Veri çeken hook ile sunum bileşeni ayrı.
- Erişilebilirlik tabanı: klavye odağı görünür, etiketler bağlı, hareket azaltma saygısı.
- Metinler kullanıcının dilinde ve eylemi net söyler ("Kaydet", "Sil"), sistem terimiyle değil.

## Tipler
- API tipleri `api/types.ts` (veya `shared/`) içinde; backend DTO'larıyla birebir. Backend değişince burası güncellenir.
