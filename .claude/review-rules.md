# Artademi — inceleme kuralları

`parsius-core:code-reviewer` bu dosyayı okur ve **evrensel kurallara + Spring stack
kurallarına ek olarak** uygular.

> Genel Spring/JPA/Keycloak kuralları (JPA alan adı, yetki ifadesi, Flyway
> disiplini, `@Builder.Default`, PK-find sızıntısı, katman ihlali, hata yönetimi)
> `parsius-spring:review-rules` skill'indedir — bu projeye project scope'ta kurulu.
> Burada **yalnız Artademi'ye özgü** olanlar var.

---

### 1. Platform katmanı ↔ tenant katmanı ayrımı — EN KRİTİK

İki katman vardır ve **asla karıştırılmaz**:

| | Platform katmanı | Tenant katmanı |
|---|---|---|
| Nerede | `com.artademi.platform` | `com.artademi.<özellik>` |
| Ne | tenant, subscription, plan | öğrenci, grup, ders, yoklama, kasa… |
| `tenant_id` | **Taşımaz** | **Taşır (NOT NULL + indeksli)** |
| Filtre | **Muaf** | Global tenant filtresine tabi |
| Kim | Yalnız `SUPER_ADMIN` | Tenant kullanıcıları |

Yeni bir tablo/servis eklendiğinde: **hangi katman?** Yanlış katman = ya veri
sızıntısı ya erişilemez özellik. Platform kodu `platform/` dışına sızmamalı.

### 2. İki tür "para" karıştırılmamalı

- **Platform geliri** — okulların bize ödediği abonelik. `subscription`, `plan`,
  platform `payment` tablolarında, tenant'ın üstünde.
- **Tenant'ın iç muhasebesi** — okulun kendi aidatları, hakedişleri, kasası.
  Tenant iş verisi, `tenant_id` taşır, abonelikle ilgisi yok.

Para ile ilgili kod eklenirken hangisi olduğu net mi? Karışma en pahalı hata.

### 3. Abonelik kapısı (gating)

Tenant durumu `SUSPENDED`/`CANCELLED` ise iş endpoint'leri **`403` +
`error.code = "TENANT_SUSPENDED"`** dönmeli; yalnız "ödeme yap / aboneliği yenile"
akışı açık kalır. **Veri silinmez.**

Yeni bir iş endpoint'i bu kapıdan geçiyor mu?

### 4. Rol semantiği

| Rol | Kapsam |
|---|---|
| `ADMIN` | Okulun her şeyi |
| `FRONTDESK` | Sınıf/grup/öğrenci/yoklama — **para tarafı YOK** |
| `FRONTDESK_ACCOUNTING` | Yukarısı + günlük kasa + ön büro giderleri — **maaş/yönetim gideri ve aylık rapor YOK** |
| `TEACHER` | Yalnız yoklama alma |
| `SUPER_ADMIN` | Platform — tenant/abonelik. Tenant iş verisine iş amaçlı girmez. |

Yeni endpoint'in rol listesi bu semantiğe uyuyor mu? Özellikle **para tarafını
`FRONTDESK`'e açmak** sessiz bir yetki genişlemesidir.

Rol **kaba** yetki verir; "sadece kendi girdiğini düzeltir" gibi ince kurallar
serviste enforce edilir (bkz. `AttendanceAccessGuard` kalıbı).

### 5. Yanıt zarfı uyumu

Tüm dönüşler `ApiResponse<T>`; sayfalı listede `PageMeta` (`page`, `size`,
`totalElements`, `totalPages`). Zarf dışı ham DTO dönen endpoint = sözleşme ihlali.

Hata `code` makine-okur sabit olmalı (`NOT_FOUND`, `VALIDATION_ERROR`,
`UNAUTHORIZED`, `CONFLICT`, `TOKEN_EXPIRED`, `TENANT_SUSPENDED`, `INTERNAL`);
`message` kullanıcıya gösterilebilir Türkçe.

Kaynak adı: **çoğul, kebab-case** — `/api/students`, `/api/lesson-sessions`.

### 6. Mobil henüz YOK

`mobile/` dizini **mevcut değil** (2026-08-03 doğrulandı). `react-native-mobile`
skill'i ileriye dönük bir plan; şu an tüketen bir mobil uygulama yok.

Bir değişiklik "mobil tarafı da güncellendi mi" gerektiriyorsa: **N/A**. Mobil kod
yazma önerisi verme; gerekiyorsa bunu bir karar noktası olarak bildir.

### 7. i18n yok

Kullanıcıya görünen metinler doğrudan Türkçe (profil: `i18n.var = false`).
Hardcoded label **ihlal değildir**. i18n kuralı **N/A**.
