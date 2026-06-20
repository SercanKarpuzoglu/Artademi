---
name: frontend-architecture
description: Artademi web (React+TS+Vite) modül kalıbı — liste/form/detay ekranları, ApiResponse zarfı, TanStack Query, RHF+Zod, rol bazlı render (dual-auth), routing ve design-reference tasarım sistemi. Yeni bir web modülü (Program, Yoklama, Finans, Hakediş, Stok, Rapor) eklerken VEYA mevcut ekranları değiştirirken bu kalıba uy. Kurallar mevcut koddan damıtıldı; dosya referanslıdır.
---

# Artademi Web — Modül Kalıbı

Dört modül (Öğrenci, Branş, Salon, Öğretmen, Grup+Kayıt) aynı kalıbı paylaşır. Yeni modül
eklerken **en yakın mevcut feature'ı kopyala**, sıfırdan türetme. Tek tasarım kaynağı
`design-reference.html` + `src/index.css` + `tailwind.config.js` — yeni global stil/renk/font yok.

Dosya yerleşimi: `api/<x>.ts` (tiplenmiş fn'ler), `api/types.ts` (DTO tipleri), `features/<x>/`
(`use<X>.ts` hook'lar, `<x>Schema.ts`, `<X>ListPage.tsx`, `<X>Form.tsx`, gerekiyorsa
`<X>DetailPage.tsx` + `<x>Display.ts` etiket/rozet map'leri), `lib/` (ortak format), `routes/menu.ts`,
`App.tsx`.

## 1. ApiResponse sözleşmesi  → `api/client.ts`, `api/types.ts`
- Tek axios instance `api` (`api/client.ts`); request interceptor taze Bearer token ekler
  (`keycloak.updateToken(30)`), response interceptor zarfı açar ve `success:false` ise
  `ApiException(code, message, fields)` fırlatır. Bileşen ASLA `fetch`/`axios` doğrudan çağırmaz.
- API fn'leri: liste **tüm zarfı** döndürür (`Promise<ApiResponse<X[]>>`) çünkü `meta` (sayfalama)
  gerekir; tekil/işlem fn'leri `res.data.data` döndürür. Örnek: `api/students.ts`, `api/branches.ts`.
- `api/types.ts`: her DTO backend ile **birebir**. `XResponse` (sunucudan) ve `XInput` (POST/PUT
  gövdesi) ayrı. Backend değişince burası güncellenir. Para alanları response'ta
  `string | number | null`, input'ta `string` (bkz. §5).
- Hata kodları (backend GlobalExceptionHandler): `NOT_FOUND`(404) · `CONFLICT`(409) ·
  `VALIDATION_ERROR`(400) · `TENANT_REQUIRED`(400) · `FORBIDDEN`(403) · `INTERNAL`(500).
  - `VALIDATION_ERROR` → `error.fields` (alan→mesaj) DOLU olabilir → input altına bas (§3).
  - Diğerleri → `error.message` kullanıcıya gösterilir (§6).

## 2. Liste ekranı kalıbı  → `features/student/StudentListPage.tsx`, `group/GroupListPage.tsx`
- `.topbar` (h1 + `.sub` + sağda `.top-actions`: "+ Yeni" yalnız yetkiliyse — §7).
- Filtre = `.tabs/.tab` (statü/aktif/tip sekmeleri). Sekme `undefined/true/false` veya enum'a maplenir.
- Arama `?q=` debounce'lu: `lib/useDebounce.ts` (300ms). `q` / filtre değişince `setPage(0)`.
- `.card` içinde `<table className="data-table">` (kompakt satır; `th` uppercase, `td` ince).
  Sayısal hücre `.t-right`, tutar `.amount` (tabular-nums).
- Sayfalama `meta`'dan (`page/size/totalElements/totalPages`); Önceki/Sonraki `.btn.btn-ghost`
  disabled sınırlarda.
- Satır tıklama → detay/düzenle; satır içi aksiyon butonlarında `e.stopPropagation()`.
- Loading/empty/error = `.card` içinde ortalanmış soluk metin (`text-ink-soft` / `text-red`).

## 3. Form kalıbı  → `features/student/StudentForm.tsx`, `<x>Schema.ts`
- RHF + `zodResolver`; `<x>Schema.ts` backend doğrulamasını **aynalar** (zorunlu/format).
  `type XFormValues = z.infer<typeof schema>` + `toPayload(values): XInput`.
- `toPayload`: boş opsiyonel metinler **gönderilmez** (`trim() || undefined`) — backend `@Pattern`
  boş string'i reddeder (`branchSchema.ts`, `studentSchema.ts`).
- Token input sınıfı (kanonik):
  `w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp`.
- **Sunucu hata eşlemesi (STANDART, 5 formda da aynı):** `catch (e)` → `e instanceof ApiException`
  → `code==='VALIDATION_ERROR' && fields` ise her alan için `setError(field, {message})`, değilse
  form-düzeyi hata kutusu. Form-düzeyi kutu sınıfı (kanonik):
  `rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red`.
- Submit sırasında buton `disabled={isSubmitting}`; başarıda `navigate('/<liste>')`.
- **Koşullu alan** (`watch`): `features/teacher/TeacherForm.tsx` (`hakedisTipi` → saatlikUcret/ciroOrani),
  `features/group/GroupForm.tsx` (`tip` → GRUP: salon+aylikAidat / OZEL: dersBasiUcret).
  Koşulu Zod `superRefine` ile de doğrula ve hatayı **ilgili alana** bağla (`saatlikUcret`/`salonId`…)
  ki sunucu `error.fields` ile aynı yere düşsün.

## 4. Detay + alt-koleksiyon kalıbı  → `features/group/GroupDetailPage.tsx`, `useEnrollments.ts`
- Route `/<x>/:id`: `.topbar` (başlık + rozet + yetkiliyse Düzenle/Listeye dön) → özet `.card`
  → ilişkili liste `.card`+`.data-table`.
- Ekle/çıkar aksiyonu liste içinde. **Silme YOK** → durum değişimi: kayıt `PATCH /{id}/leave`
  (AYRILDI), tanımlar `PATCH /{id}/active`. Yıkıcı/çıkar aksiyonunda `window.confirm`.
- İlişkili kayıt eklerken **aramalı seçici** (tüm listeyi çekme): `getStudents({q, size:10})` debounce.

## 5. Dropdown reuse + pasif referans sentezi  → `features/group/GroupForm.tsx`
- İlişkili entity seçimi feature hook'larından **tekrar kullanılır**: `useBranches/useRooms/useTeachers`
  (`{ aktif:true, size:200 }` — yalnız aktif kayıtlar; pasif salona yeni grup açılmasın).
- **Kaybolmama kuralı:** düzenlemede mevcut seçili referans pasifse aktif listede yoktur; bu yüzden
  `XResponse.brans/salon/ogretmen` özetinden bir option **sentezlenip** listeye eklenir
  (`GroupForm.tsx` `branchOptions/teacherOptions/roomOptions` + `loaded?.x && !options.some(...)`).
- Para hassasiyeti: BigDecimal alanları **string** serileştir (`toPayload` ilgili para alanını
  trim'li string gönderir; tipe göre yalnız geçerli olanı). Gösterim: `lib/format.ts` `formatMoney`
  (sayı VEYA string'i tolere eder) + `.amount` sınıfı.

## 6. TanStack Query kalıbı  → tüm `use<X>.ts`
- Liste: `useQuery({ queryKey:['xs', params], queryFn, placeholderData: keepPreviousData })`.
- Tekil: `['x', id]`, `enabled: id !== undefined`. Alt-koleksiyon: `['x', id, 'enrollments', {durum}]`.
- Mutasyonlar liste + ilgili kaydı invalidate eder: create → `['xs']`; update → `['xs']`+`['x',id]`;
  setActive → `['xs']`; leave/ekle → `['x', id, 'enrollments']` (`useEnrollments.ts`).

## 7. Rol bazlı render / gating  → `auth/AuthContext.tsx`, `routes/RoleRoute.tsx`
- `const { hasRole, hasAnyRole } = useAuth();` — roller token'ın `realm_access.roles`'ından,
  teknik roller `roles.ts` `filterDomainRoles` ile elenir. `Role` const'u tek kaynak (typo yok).
- Menü gizleme + buton gizleme **yalnız UX**; asıl güvenlik backend'de. Route koruması `RoleRoute`
  (`requiredRoles` yoksa `/403`).
- **DUAL-AUTH DERSİ (en kritik incelik):** *tanım yazma* ile *operasyon yazma* farklı olabilir.
  Örnek (`features/group/`): grup tanımı yazma (+Yeni/Düzenle/aktiflik) = **sadece ADMIN**
  (`hasRole(Role.ADMIN)`), ama kayıt yazma (öğrenci ekle/çıkar) = **ADMIN+FRONTDESK+FRONTDESK_ACCOUNTING**
  (`hasAnyRole([...])`). Aynı sayfada iki ayrı gating; karıştırma. Okuma genelde ofis 3 rolü, TEACHER→403.
- Bu projede tipik matris: tanım modülleri (Branş/Salon/Öğretmen/Grup) yazma=ADMIN, okuma=ofis 3;
  finans/maaş hassas uçlar daha dar (bkz. backend keycloak-auth). Doğru roller için job/handoff'a bak.

## 8. Routing  → `App.tsx`, `routes/menu.ts`
- Route seti: `/<x>` (liste), `/<x>/yeni`, `/<x>/:id` (detay, gerekiyorsa), `/<x>/:id/duzenle`.
- Hepsi AppShell altında, `<RoleRoute requiredRoles={OFIS}>` (veya modüle uygun roller) ile sarılı.
  `OFIS = [ADMIN, FRONTDESK, FRONTDESK_ACCOUNTING]` (`App.tsx`).
- `routes/menu.ts`: menü öğesi `{ label, path, icon (lucide), section, roles, hazir }`. Yeni modül
  hazır olunca `hazir: true` yap; placeholder route'u (`ComingSoonPage`) gerçek sayfayla değiştir.
  Sidebar bölümleri `section` ile gruplanır (Genel / Eğitim / Tanımlar / İşletme).

## 9. Tasarım bağı  → `index.css`, `tailwind.config.js`, `design-reference.html`
- Sınıflar: `.topbar/.sub`, `.card`, `.data-table` + `.amount/.t-right`, `.tabs/.tab`,
  `.btn/.btn-primary/.btn-ghost`, `.badge .b-green/.b-red/.b-amber/.b-rasp/.b-blue/.b-gray`.
- Token renkler: `ink/paper/card/line/rasp(+soft)/green/red/amber/blue(+soft)/gray-soft`; fontlar
  `fraunces` (başlık) + `manrope` (gövde). **Hex gömme yok**, token kullan.
- Rozet eşlemeleri feature'ın `<x>Display.ts`'inde (`teacherDisplay.ts`, `groupDisplay.ts`):
  durum/tip → label + `.b-*`. Statü: AKTIF→b-green, DENEME→b-amber, PASIF→b-gray, DONDURULMUS→b-blue
  (`components/StatusBadge.tsx`).

## 10. Yapma listesi (anti-patterns)
- Bileşenden doğrudan `fetch`/`axios` — hep `api/<x>.ts`.
- Tüm kayıtları çekip istemcide filtreleme — aramalı `?q=` seçici/sunucu filtresi kullan.
- Hex/renk/font gömme — token + mevcut sınıflar.
- Token'ı `localStorage`'a yazma — keycloak-js bellekte tutar (`lib/keycloak.ts`).
- Ortak yardımcıyı feature'a gömme — para formatı `lib/format.ts`'te (feature'a değil).
- Yeni global CSS sınıfı uydurma — `design-reference.html`'de yoksa önce mevcutları kullan.
- Rol gating'i tek kalıba indirgeme — dual-auth'u (tanım vs operasyon) unutma.

## Kural → dosya eşlemesi
| Kural | Kanıt dosyası |
|---|---|
| Zarf açma + ApiException | `src/api/client.ts` |
| DTO tipleri (Response/Input ayrı) | `src/api/types.ts` |
| Liste fn tüm zarfı döner | `src/api/students.ts`, `src/api/branches.ts` |
| Liste ekranı (topbar/tabs/data-table/meta) | `src/features/student/StudentListPage.tsx`, `group/GroupListPage.tsx` |
| Form + error.fields eşleme (5 formda aynı) | `src/features/*/{Branch,Room,Teacher,Student,Group}Form.tsx` |
| Zod şema + toPayload (boş→undefined) | `src/features/branch/branchSchema.ts`, `student/studentSchema.ts` |
| Koşullu alan (watch + superRefine) | `src/features/teacher/TeacherForm.tsx`, `group/groupSchema.ts` |
| Detay + alt-koleksiyon + leave | `src/features/group/GroupDetailPage.tsx`, `group/useEnrollments.ts` |
| Dropdown reuse + pasif sentezi | `src/features/group/GroupForm.tsx` (`useBranches/useRooms/useTeachers`) |
| Para string + formatMoney | `toPayload` (group/teacher), `src/lib/format.ts` |
| Query key + invalidate | `src/features/branch/useBranches.ts`, `group/useEnrollments.ts` |
| Rol/gating + dual-auth | `src/features/group/GroupListPage.tsx` (ADMIN) vs `GroupDetailPage.tsx` (ENROLLMENT_ROLES) |
| RoleRoute / 403 / menü | `src/routes/RoleRoute.tsx`, `src/routes/menu.ts`, `src/App.tsx` |
| Tasarım sistemi | `src/index.css`, `tailwind.config.js`, `design-reference.html` |
